package io.kestra.queue.jdbc.client;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.google.common.base.CaseFormat;
import io.kestra.queue.QueueService;
import org.apache.commons.lang3.tuple.Pair;
import org.jooq.*;
import org.jooq.Record;
import org.jooq.exception.DataException;
import org.jooq.impl.DSL;

import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.UnsupportedMessageException;
import io.kestra.jdbc.AbstractJdbcRepository;
import io.kestra.jdbc.JdbcJsonbUtils;
import io.kestra.jdbc.JdbcQueueItem;
import io.kestra.jdbc.JooqDSLContextWrapper;
import io.kestra.jdbc.runner.JdbcQueueConfiguration;

import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.Getter;

import static io.kestra.jdbc.repository.AbstractJdbcRepository.field;

@Singleton
public class JdbcQueueClient {
    public static final Field<String> TYPE = field("type", String.class);
    public static final Field<String> ROUTING_KEY = field("routing_key", String.class);
    public static final Field<String> KEY = field("key", String.class);
    public static final Field<JSONB> VALUE = field("value", JSONB.class);
    public static final Field<Instant> CREATED = field("created", Instant.class);
    public static final Field<Long> OFFSET = field("offset", Long.class);

    private static final List<Field<?>> COLUMNS = List.of(
        TYPE,
        ROUTING_KEY,
        KEY,
        VALUE,
        CREATED
    );

    private final AbstractJdbcRepository<JdbcQueueItem> jdbcRepository;

    private final JooqDSLContextWrapper dslContextWrapper;

    @Getter
    private final JdbcQueueConfiguration configuration;
    private final QueueService queueService;

    @Inject
    public JdbcQueueClient(@Named("queues") AbstractJdbcRepository<JdbcQueueItem> jdbcRepository, JooqDSLContextWrapper dslContextWrapper, JdbcQueueConfiguration configuration, QueueService queueService) {
        this.jdbcRepository = jdbcRepository;
        this.dslContextWrapper = dslContextWrapper;
        this.configuration = configuration;
        this.queueService = queueService;
    }

    private boolean isUnsupportedUnicode(DataException e) {
        Throwable current = e;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String lower = message.toLowerCase();
                if (
                    message.contains("unsupported Unicode escape sequence") ||
                        lower.contains("surrogate") ||
                        lower.contains("unicode escape") ||
                        lower.contains("invalid unicode")
                ) {
                    return true;
                }
            }

            current = current.getCause();
        }

        return false;
    }

    public void publish(String queue, @Nullable String routingKey, String key, String value) throws QueueException {
        try {
            dslContextWrapper.transaction(configuration ->
            {
                DSLContext context = DSL.using(configuration);

                Map<Field<?>, Object> fields = HashMap.newHashMap(5);
                fields.put(TYPE, queue);
                fields.put(ROUTING_KEY, (routingKey == null || routingKey.isEmpty()) ? null : routingKey);
                fields.put(KEY, key);
                fields.put(VALUE, JdbcJsonbUtils.valueOf(value));
                fields.put(CREATED, Instant.now());

                var insert = context
                    .insertInto(getTable(queue))
                    .set(fields);

                insert.execute();
            });
        } catch (DataException e) { // The exception is from the data itself, not the database/network/driver so instead of fail fast, we throw a recoverable QueueException
            // Postgres refuses JSONB payloads with unsupported Unicode escape sequences such as '\0000'
            // or lone UTF-16 surrogates. Convert those into a recoverable queue error.
            // We try to detect that and fail with a specific exception so the Worker can recover from it.
            if (isUnsupportedUnicode(e)) {
                throw new UnsupportedMessageException(e.getMessage(), e);
            }
            throw new QueueException("Unable to emit a message to the queue", e);
        }
    }

    public Integer queueLag(String queue, @Nullable String routingKey) {
        return dslContextWrapper.transactionResult(configuration ->
        {
            DSLContext ctx = DSL.using(configuration);

            var condition = TYPE.eq(queue);
            if (routingKey != null && !routingKey.isEmpty()) {
                condition = condition.and(ROUTING_KEY.eq(routingKey));
            } else {
                condition = condition.and(ROUTING_KEY.isNull());
            }

            return ctx.selectCount()
                .from(getTable(queue))
                .where(condition)
                .fetchOneInto(Integer.class);
        });
    }

    public record PublishedMessage(String queue, String routingKey, String key, String value) {
    }

    public void publish(List<PublishedMessage> messages) throws QueueException {
        if (messages.isEmpty()) {
            return;
        }

        try {
            dslContextWrapper.transaction(configuration ->
            {
                DSLContext context = DSL.using(configuration);

                InsertValuesStepN<Record> insert = context
                    // all messages are expected to be for the same queue
                    .insertInto(getTable(messages.getFirst().queue))
                    .columns(COLUMNS);

                Instant now = Instant.now();
                for (PublishedMessage entry : messages) {
                    insert = insert.values(
                        entry.queue,
                        (entry.routingKey == null || entry.routingKey.isEmpty()) ? null : entry.routingKey,
                        entry.key,
                        JdbcJsonbUtils.valueOf(entry.value),
                        now
                    );
                }

                insert.execute();
            });
        } catch (DataException e) { // The exception is from the data itself, not the database/network/driver so instead of fail fast, we throw a recoverable QueueException
            // Postgres refuses JSONB payloads with unsupported Unicode escape sequences such as '\0000'
            // or lone UTF-16 surrogates. Convert those into a recoverable queue error.
            // We try to detect that and fail with a specific exception so the Worker can recover from it.
            if (isUnsupportedUnicode(e)) {
                throw new UnsupportedMessageException(e.getMessage(), e);
            }
            throw new QueueException("Unable to emit a message to the queue", e);
        }
    }

    public Integer subscribeDispatch(String queue, @Nullable List<String> routingKeys, Consumer<byte[]> consumer) {
        return dslContextWrapper.transactionResult(conf ->
        {
            DSLContext context = DSL.using(conf);

            var select = context.select(OFFSET, VALUE)
                .from(getTable(queue))
                .where(TYPE.eq(queue));

            if (routingKeys != null && !routingKeys.isEmpty()) {
                select = select.and(ROUTING_KEY.in(routingKeys));
            } else {
                select = select.and(ROUTING_KEY.isNull());
            }

            var result = select
                .orderBy(OFFSET.asc())
                .limit(configuration.pollSize())
                .forUpdate()
                .skipLocked()
                .fetch();

            if (!result.isEmpty()) {
                List<Long> processedItems = result
                    .stream()
                    .map(record ->
                    {
                        consumer.accept(record.get(VALUE).data().getBytes(StandardCharsets.UTF_8));
                        return record.get(OFFSET);
                    })
                    .toList();

                if (!processedItems.isEmpty()) {
                    DeleteConditionStep<Record> delete = context.delete(getTable(queue))
                        .where(OFFSET.in(processedItems));

                    delete.execute();
                }
            }

            return result.size();
        });
    }

    public Integer subscribeDispatchBatch(String queue, List<String> routingKeys, Consumer<List<byte[]>> consumer) {
        return dslContextWrapper.transactionResult(conf ->
        {
            DSLContext context = DSL.using(conf);

            var select = context.select(OFFSET, VALUE)
                .from(getTable(queue))
                .where(TYPE.eq(queue));

            if (routingKeys != null && !routingKeys.isEmpty()) {
                select = select.and(ROUTING_KEY.in(routingKeys));
            } else {
                select = select.and(ROUTING_KEY.isNull());
            }

            var result = select
                .orderBy(OFFSET.asc())
                .limit(configuration.pollSize())
                .forUpdate()
                .skipLocked()
                .fetch();

            if (!result.isEmpty()) {
                consumer.accept(result.stream().map(record -> record.get(VALUE).data().getBytes(StandardCharsets.UTF_8)).toList());

                List<Long> processedItems = result
                    .stream()
                    .map(record -> record.get(OFFSET))
                    .toList();

                DeleteConditionStep<Record> delete = context.delete(getTable(queue))
                    .where(OFFSET.in(processedItems));
                delete.execute();
            }

            return result.size();
        });
    }

    public @Nullable Long fetchMaxOffset(String queue) {
        Long initialOffset = dslContextWrapper.transactionResult(conf ->
        {
            DSLContext context = DSL.using(conf);

            // Filters identically to subscribeBroadcast/subscribeBroadcastBatch so the seeded
            // offset and the poll queries always operate over the same row set.
            return context.select(DSL.max(OFFSET))
                .from(getTable(queue))
                .where(TYPE.eq(queue))
                .and(ROUTING_KEY.isNull())
                .fetchAny("max", Long.class);
        });

        return initialOffset != null ? initialOffset : 0L;
    }

    protected Pair<Integer, Long> subscribeBroadcast(String queue, @Nullable Long maxOffset, Consumer<byte[]> consumer) {
        return dslContextWrapper.transactionResult(conf ->
        {
            DSLContext context = DSL.using(conf);
            Long maxOffsetResult = null;

            // Broadcast messages are always published with a null routing key. Binding it explicitly here
            // (instead of leaving it unconstrained) lets the (type, routing_key, offset) index be used as a
            // seek on offset instead of a full scan of every retained row for this type on each poll.
            var select = context.select(OFFSET, VALUE)
                .from(getTable(queue))
                .where(TYPE.eq(queue))
                .and(ROUTING_KEY.isNull());

            if (maxOffset != null) {
                select = select.and(OFFSET.gt(maxOffset));
            }

            var result = select
                .orderBy(OFFSET.asc())
                .limit(configuration.pollSize())
                .fetch();

            if (!result.isEmpty()) {
                result.forEach(record -> consumer.accept(record.get(VALUE).data().getBytes(StandardCharsets.UTF_8)));

                maxOffsetResult = result
                    .stream()
                    .map(record -> record.get(OFFSET))
                    .max(Long::compareTo)
                    .orElse(null);
            }

            return Pair.of(result.size(), maxOffsetResult != null ? maxOffsetResult : maxOffset);
        });
    }

    public Pair<Integer, Long> subscribeBroadcastBatch(String queue, Long maxOffset, Consumer<List<byte[]>> consumer) {
        return dslContextWrapper.transactionResult(conf ->
        {
            DSLContext context = DSL.using(conf);
            Long maxOffsetResult = null;

            // Broadcast messages are always published with a null routing key. Binding it explicitly here
            // (instead of leaving it unconstrained) lets the (type, routing_key, offset) index be used as a
            // seek on offset instead of a full scan of every retained row for this type on each poll.
            var select = context.select(OFFSET, VALUE)
                .from(getTable(queue))
                .where(TYPE.eq(queue))
                .and(ROUTING_KEY.isNull());

            if (maxOffset != null) {
                select = select.and(OFFSET.gt(maxOffset));
            }

            var result = select
                .orderBy(OFFSET.asc())
                .limit(configuration.pollSize())
                .fetch();

            if (!result.isEmpty()) {
                consumer.accept(result.stream().map(record -> record.get(VALUE).data().getBytes(StandardCharsets.UTF_8)).toList());

                maxOffsetResult = result
                    .stream()
                    .map(record -> record.get(OFFSET))
                    .max(Long::compareTo)
                    .orElse(null);
            }

            return Pair.of(result.size(), maxOffsetResult != null ? maxOffsetResult : maxOffset);
        });
    }

    private Table<Record> getTable(String queue) {
        var tableName = "queues_" + queue; // FIXME should comes from table config
        return DSL.table(tableName); // TODO we should cache them to avoid re-creating the object on each call
    }

    public void createTableIfNotExist(Class<?> cls) {
        // FIXME fragile and PG only !!!
        String queueName = "queues_" + queueName(cls); // FIXME should comes from table config
        String creatTable = """
            CREATE TABLE IF NOT EXISTS %s (
                "offset"      SERIAL       PRIMARY KEY,
                type          VARCHAR(250) NOT NULL,
                "routing_key" VARCHAR(250),
                key           VARCHAR(250) NOT NULL,
                value         JSONB        NOT NULL,
                created       TIMESTAMPTZ  NOT NULL
            );
            """.formatted(queueName);
        String createIndex1 = """
            CREATE INDEX IF NOT EXISTS %s_type__key__offset ON %s (type, "routing_key", "offset");
            """.formatted(queueName, queueName);
        String createIndex2 = """
            CREATE INDEX IF NOT EXISTS %s_type__created ON %s ("type", "created");
            """.formatted(queueName, queueName);
        dslContextWrapper.transaction(conf -> {
            conf.dsl().execute(creatTable);
            conf.dsl().execute(createIndex1);
            conf.dsl().execute(createIndex2);
        });
    }

    // TODO duplicated with AbstractQueue
    private String queueName(Class<?> cls) {
        String result = "";

        if (queueService.getQueueConfiguration().getPrefix() != null) {
            result = queueService.getQueueConfiguration().getPrefix() + "__";
        }

        return result + CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, cls.getSimpleName());
    }
}
