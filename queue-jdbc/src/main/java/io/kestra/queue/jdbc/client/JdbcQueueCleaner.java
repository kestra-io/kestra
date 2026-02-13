package io.kestra.queue.jdbc.client;

import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.jdbc.JdbcTableConfig;
import io.kestra.jdbc.JooqDSLContextWrapper;
import io.kestra.jdbc.repository.AbstractJdbcRepository;
import io.kestra.jdbc.runner.JdbcQueueEnabled;
import io.micronaut.context.annotation.Property;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.jooq.*;
import org.jooq.Record;
import org.jooq.impl.DSL;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.Temporal;
import java.util.List;
import java.util.concurrent.atomic.LongAdder;

@Singleton
@JdbcQueueEnabled
@Slf4j
public class JdbcQueueCleaner {
    private static final Field<Object> CREATED_FIELD = AbstractJdbcRepository.field("created");
    private static final Field<Object> TYPE_FIELD = AbstractJdbcRepository.field("type");
    private static final int MYSQL_BATCH_SIZE = 10_000;

    private final JooqDSLContextWrapper dslContextWrapper;
    private final Table<Record> queueTable;
    private final List<BroadcastQueueInterface<?>> broadcastQueues;
    private final Duration retention;

    @Inject
    public JdbcQueueCleaner(@Named("queue") JdbcTableConfig jdbcTableConfig,
                            JooqDSLContextWrapper dslContextWrapper,
                            List<BroadcastQueueInterface<?>> broadcastQueues,
                            @Property(name = "kestra.jdbc.queue.cleaner.retention", defaultValue = "1h") Duration retention
    ) {
        this.dslContextWrapper = dslContextWrapper;
        this.broadcastQueues = broadcastQueues;
        this.retention = retention;

        this.queueTable = DSL.table(jdbcTableConfig.table());
    }

    @Scheduled(initialDelay = "${kestra.jdbc.queue.cleaner.initial-delay:1h}", fixedDelay = "${kestra.jdbc.queue.cleaner.fixed-delay:1h}")
    public long deleteQueue() {
        LongAdder totalDeleted = new LongAdder();
        broadcastQueues.forEach(queue -> {
            Integer queueType = JdbcQueueClient.queueNameToType(queue.queueName());
            dslContextWrapper.transaction(configuration -> {
                var condition = CREATED_FIELD.lessOrEqual(period(configuration, retention)).and(TYPE_FIELD.eq(queueType));
                int deleted = delete(configuration, condition);
                log.info("Cleaned {} records for the '{}' queue", deleted, queue.queueName());
                totalDeleted.add(deleted);
            });
        });

        return totalDeleted.longValue();
    }

    private int delete(org.jooq.Configuration configuration, Condition condition) {
        if (configuration.dialect().family() == SQLDialect.MYSQL) {
            // MySQL struggle with large transactions so we need to execute them in batch
            int totalDeleted = 0;
            int subDeleted;
            do {
                subDeleted = DSL
                    .using(configuration)
                    .delete(this.queueTable)
                    .where(condition)
                    .limit(MYSQL_BATCH_SIZE)
                    .execute();
                totalDeleted += subDeleted;
            } while (subDeleted > 0);
            return totalDeleted;
        } else {
            return DSL
                .using(configuration)
                .delete(this.queueTable)
                .where(condition)
                .execute();
        }
    }

    private Temporal period(org.jooq.Configuration configuration, Duration retention) {
        if (configuration.dialect().family() == SQLDialect.MYSQL) {
            // 'date' column in the table is in local time for MySQL
            return ZonedDateTime.now().minus(retention).toLocalDateTime();
        }
        return ZonedDateTime.now().minus(retention).toOffsetDateTime();
    }
}
