package io.kestra.queue.jdbc.client;

import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.UnsupportedMessageException;
import io.kestra.jdbc.AbstractJdbcRepository;
import io.kestra.jdbc.JdbcQueueItem;
import io.kestra.jdbc.JooqDSLContextWrapper;
import io.kestra.jdbc.runner.JdbcQueueConfiguration;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.Getter;
import org.apache.commons.lang3.tuple.Pair;
import org.jooq.*;
import org.jooq.Record;
import org.jooq.exception.DataException;
import org.jooq.impl.DSL;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.CRC32;

@Singleton
public class JdbcQueueClient {
    private static final Map<String, Integer> QUEUE_NAME_CRC32 = new ConcurrentHashMap<>();
    private static final List<Field<Object>> COLUMNS = List.of(
        io.kestra.jdbc.repository.AbstractJdbcRepository.field("type"),
        io.kestra.jdbc.repository.AbstractJdbcRepository.field("routing_key"),
        io.kestra.jdbc.repository.AbstractJdbcRepository.field("key"),
        io.kestra.jdbc.repository.AbstractJdbcRepository.field("value"),
        io.kestra.jdbc.repository.AbstractJdbcRepository.field("created")
    );

    private final AbstractJdbcRepository<JdbcQueueItem> jdbcRepository;

    private final JooqDSLContextWrapper dslContextWrapper;

    @Getter
    private final JdbcQueueConfiguration configuration;

    @Inject
    public JdbcQueueClient(@Named("queue") AbstractJdbcRepository<JdbcQueueItem> jdbcRepository, JooqDSLContextWrapper dslContextWrapper, JdbcQueueConfiguration configuration) {
        this.jdbcRepository = jdbcRepository;
        this.dslContextWrapper = dslContextWrapper;
        this.configuration = configuration;
    }

    public static Integer queueNameToType(String value) {
        return QUEUE_NAME_CRC32.computeIfAbsent(value, s -> {
            CRC32 crc32 = new CRC32();
            crc32.update(value.getBytes());

            return (int) crc32.getValue();
        });
    }

    public void publish(String queue, @Nullable String routingKey, String key, String value) throws QueueException {
        this.publish(queue, routingKey, Map.of(key, value));
    }

    public void publish(String queue, @Nullable String routingKey, Map<String, String> values) throws QueueException {
        try {
            dslContextWrapper.transaction(configuration -> {
                DSLContext context = DSL.using(configuration);

                InsertValuesStepN<Record> insert = context
                    .insertInto(jdbcRepository.getTable())
                    .columns(COLUMNS);

                for (Map.Entry<String, String> entry : values.entrySet()) {
                    insert = insert.values(
                        queueNameToType(queue),
                        routingKey,
                        entry.getKey(),
                        JSONB.valueOf(entry.getValue()),
                        Instant.now()
                    );
                }

                insert.execute();
            });
        } catch (DataException e) { // The exception is from the data itself, not the database/network/driver so instead of fail fast, we throw a recoverable QueueException
            // Postgres refuses to store JSONB with the '\0000' codepoint as it has no textual representation.
            // We try to detect that and fail with a specific exception so the Worker can recover from it.
            if (e.getMessage() != null && e.getMessage().contains("ERROR: unsupported Unicode escape sequence")) {
                throw new UnsupportedMessageException(e.getMessage(), e);
            }
            throw new QueueException("Unable to emit a message to the queue", e);
        }
    }

    public Integer subscribeDispatch(String queue, @Nullable String routingKey, MessageConsumer<String, Exception> consumer) {
        return dslContextWrapper.transactionResult(conf -> {
            DSLContext context = DSL.using(conf);

            SelectConditionStep<Record> select = context.select(DSL.asterisk())
                .from(this.jdbcRepository.getTable())
                .where(io.kestra.jdbc.repository.AbstractJdbcRepository.field("type").eq(queueNameToType(queue)));

            if (routingKey != null) {
                select = select.and(io.kestra.jdbc.repository.AbstractJdbcRepository.field("routing_key").eq(routingKey));
            }

            List<JdbcQueueItem> queueItems = select
                .orderBy(io.kestra.jdbc.repository.AbstractJdbcRepository.field("offset").asc())
                .limit(configuration.pollSize())
                .forUpdate()
                .skipLocked()
                .fetchInto(JdbcQueueItem.class);

            if (!queueItems.isEmpty()) {
                List<Long> processedItems = queueItems
                    .stream()
                    .map(queueItem -> {
                        Exception exception = consumer.apply(queueItem.value());
                        return exception == null ? queueItem.offset() : null;
                    })
                    .filter(Objects::nonNull)
                    .toList();

                if (!processedItems.isEmpty()) {
                    DeleteConditionStep<Record> delete = context.delete(this.jdbcRepository.getTable())
                        .where(io.kestra.jdbc.repository.AbstractJdbcRepository.field("type").eq(queueNameToType(queue)))
                        .and(io.kestra.jdbc.repository.AbstractJdbcRepository.field("offset", Long.class).in(processedItems));

                    if (routingKey != null) {
                        delete = delete.and(io.kestra.jdbc.repository.AbstractJdbcRepository.field("routing_key").eq(routingKey));
                    }

                    delete.execute();
                }
            }

            return queueItems.size();
        });
    }

    public @Nullable Long fetchMaxOffset(String queue) {
        Long maxOffset = null;

        Long initialOffset = dslContextWrapper.transactionResult(conf -> {
            DSLContext context = DSL.using(conf);

            return context.select(DSL.max(io.kestra.jdbc.repository.AbstractJdbcRepository.field("offset")))
                .from(this.jdbcRepository.getTable())
                .where(io.kestra.jdbc.repository.AbstractJdbcRepository.field("type").eq(queueNameToType(queue)))
                .fetchAny("max", Long.class);
        });

        if (initialOffset != null) {
            maxOffset = initialOffset;
        }

        return maxOffset;
    }

    protected Pair<Integer, Long> subscribeBroadcast(String queue, @Nullable Long maxOffset, MessageConsumer<String, Exception> consumer) {
        return dslContextWrapper.transactionResult(conf -> {
            DSLContext context = DSL.using(conf);
            Long maxOffsetResult = null;

            SelectConditionStep<Record> select = context.select(DSL.asterisk())
                .from(this.jdbcRepository.getTable())
                .where(io.kestra.jdbc.repository.AbstractJdbcRepository.field("type").eq(queueNameToType(queue)));

            if (maxOffset != null) {
                select = select.and(io.kestra.jdbc.repository.AbstractJdbcRepository.field("offset").gt(maxOffset));
            }

            List<JdbcQueueItem> queueItems = select
                .orderBy(io.kestra.jdbc.repository.AbstractJdbcRepository.field("offset").asc())
                .limit(configuration.pollSize())
                .forUpdate()
                .skipLocked()
                .fetchInto(JdbcQueueItem.class);

            if (!queueItems.isEmpty()) {
                queueItems
                    .forEach(queueItem -> {
                        consumer.apply(queueItem.value());
                    });

                maxOffsetResult = queueItems
                    .stream()
                    .map(JdbcQueueItem::offset)
                    .max(Long::compareTo)
                    .orElse(null);
            }

            return Pair.of(queueItems.size(), maxOffsetResult);
        });
    }

    @FunctionalInterface
    public interface MessageConsumer <T, E extends Exception> {
         @Nullable E apply(T t);
    }
}
