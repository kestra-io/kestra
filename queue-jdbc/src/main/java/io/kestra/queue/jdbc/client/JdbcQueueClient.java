package io.kestra.queue.jdbc.client;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.zip.CRC32;

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
    private static final Map<String, Integer> QUEUE_NAME_CRC32 = new ConcurrentHashMap<>();
    private static final List<Field<Object>> COLUMNS = List.of(
        field("type"),
        field("routing_key"),
        field("key"),
        field("value"),
        field("created")
    );

    private final AbstractJdbcRepository<JdbcQueueItem> jdbcRepository;

    private final JooqDSLContextWrapper dslContextWrapper;

    @Getter
    private final JdbcQueueConfiguration configuration;

    @Inject
    public JdbcQueueClient(@Named("queues") AbstractJdbcRepository<JdbcQueueItem> jdbcRepository, JooqDSLContextWrapper dslContextWrapper, JdbcQueueConfiguration configuration) {
        this.jdbcRepository = jdbcRepository;
        this.dslContextWrapper = dslContextWrapper;
        this.configuration = configuration;
    }

    public static Integer queueNameToType(String value) {
        return QUEUE_NAME_CRC32.computeIfAbsent(value, s ->
        {
            CRC32 crc32 = new CRC32();
            crc32.update(value.getBytes());

            return (int) crc32.getValue();
        });
    }

    private boolean isUnsupportedUnicode(DataException e) {
        Throwable current = e;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String lower = message.toLowerCase();
                if (message.contains("unsupported Unicode escape sequence") ||
                    lower.contains("surrogate") ||
                    lower.contains("unicode escape") ||
                    lower.contains("invalid unicode")) {
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

                Map<Field<Object>, Object> fields = HashMap.newHashMap(5);
                fields.put(field("type"), queueNameToType(queue));
                fields.put(field("routing_key"), (routingKey == null || routingKey.isEmpty()) ? null : routingKey);
                fields.put(field("key"), key);
                fields.put(field("value"), JdbcJsonbUtils.valueOf(value));
                fields.put(field("created"), Instant.now());

                var insert = context
                    .insertInto(jdbcRepository.getTable())
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

            var condition = field("type").eq(queueNameToType(queue));
            if (routingKey != null && !routingKey.isEmpty()) {
                condition = condition.and(field("routing_key").eq(routingKey));
            } else {
                condition = condition.and(field("routing_key").isNull());
            }

            return ctx.selectCount()
                .from(jdbcRepository.getTable())
                .where(condition)
                .fetchOneInto(Integer.class);
        });
    }

    public record PublishedMessage(String queue, String routingKey, String key, String value) {
    }

    public void publish(List<PublishedMessage> messages) throws QueueException {
        try {
            dslContextWrapper.transaction(configuration ->
            {
                DSLContext context = DSL.using(configuration);

                InsertValuesStepN<Record> insert = context
                    .insertInto(jdbcRepository.getTable())
                    .columns(COLUMNS);

                // TODO check if we should not do a batch insert instead
                Instant now = Instant.now();
                for (PublishedMessage entry : messages) {
                    insert = insert.values(
                        queueNameToType(entry.queue),
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

            SelectConditionStep<Record2<Object, Object>> select = context.select(field("offset"), field("value"))
                .from(this.jdbcRepository.getTable())
                .where(field("type").eq(queueNameToType(queue)));

            if (routingKeys != null && !routingKeys.isEmpty()) {
                select = select.and(field("routing_key").in(routingKeys));
            } else {
                select = select.and(field("routing_key").isNull());
            }

            Result<Record2<Object, Object>> result = select
                .orderBy(field("offset").asc())
                .limit(configuration.pollSize())
                .forUpdate()
                .skipLocked()
                .fetch();

            if (!result.isEmpty()) {
                List<Long> processedItems = result
                    .stream()
                    .map(record ->
                    {
                        consumer.accept(record.get("value").toString().getBytes());
                        return record.get("offset", Long.class);
                    })
                    .filter(Objects::nonNull)
                    .toList();

                if (!processedItems.isEmpty()) {
                    DeleteConditionStep<Record> delete = context.delete(this.jdbcRepository.getTable())
                        .where(field("offset", Long.class).in(processedItems));

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

            SelectConditionStep<Record2<Object, Object>> select = context.select(field("offset"), field("value"))
                .from(this.jdbcRepository.getTable())
                .where(field("type").eq(queueNameToType(queue)));

            if (routingKeys != null && !routingKeys.isEmpty()) {
                select = select.and(field("routing_key").in(routingKeys));
            } else {
                select = select.and(field("routing_key").isNull());
            }

            Result<Record2<Object, Object>> result = select
                .orderBy(field("offset").asc())
                .limit(configuration.pollSize())
                .forUpdate()
                .skipLocked()
                .fetch();

            if (!result.isEmpty()) {
                consumer.accept(result.stream().map(record -> record.get("value").toString().getBytes()).toList());

                List<Long> processedItems = result
                    .stream()
                    .map(record -> record.get("offset", Long.class))
                    .toList();

                DeleteConditionStep<Record> delete = context.delete(this.jdbcRepository.getTable())
                    .where(field("offset", Long.class).in(processedItems));
                delete.execute();
            }

            return result.size();
        });
    }

    public @Nullable Long fetchMaxOffset(String queue) {
        Long initialOffset = dslContextWrapper.transactionResult(conf ->
        {
            DSLContext context = DSL.using(conf);

            return context.select(DSL.max(field("offset")))
                .from(this.jdbcRepository.getTable())
                .where(field("type").eq(queueNameToType(queue)))
                .fetchAny("max", Long.class);
        });

        return initialOffset != null ? initialOffset : 0L;
    }

    protected Pair<Integer, Long> subscribeBroadcast(String queue, @Nullable Long maxOffset, Consumer<byte[]> consumer) {
        return dslContextWrapper.transactionResult(conf ->
        {
            DSLContext context = DSL.using(conf);
            Long maxOffsetResult = null;

            SelectConditionStep<Record2<Object, Object>> select = context.select(field("offset"), field("value"))
                .from(this.jdbcRepository.getTable())
                .where(field("type").eq(queueNameToType(queue)));

            if (maxOffset != null) {
                select = select.and(field("offset").gt(maxOffset));
            }

            Result<Record2<Object, Object>> result = select
                .orderBy(field("offset").asc())
                .limit(configuration.pollSize())
                .fetch();

            if (!result.isEmpty()) {
                result.forEach(record -> consumer.accept(record.get("value").toString().getBytes()));

                maxOffsetResult = result
                    .stream()
                    .map(record -> record.get("offset", Long.class))
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

            SelectConditionStep<Record2<Object, Object>> select = context.select(field("offset"), field("value"))
                .from(this.jdbcRepository.getTable())
                .where(field("type").eq(queueNameToType(queue)));

            if (maxOffset != null) {
                select = select.and(field("offset").gt(maxOffset));
            }

            Result<Record2<Object, Object>> result = select
                .orderBy(field("offset").asc())
                .limit(configuration.pollSize())
                .fetch();

            if (!result.isEmpty()) {
                consumer.accept(result.stream().map(record -> record.get("value").toString().getBytes()).toList());

                maxOffsetResult = result
                    .stream()
                    .map(record -> record.get("offset", Long.class))
                    .max(Long::compareTo)
                    .orElse(null);
            }

            return Pair.of(result.size(), maxOffsetResult != null ? maxOffsetResult : maxOffset);
        });
    }
}
