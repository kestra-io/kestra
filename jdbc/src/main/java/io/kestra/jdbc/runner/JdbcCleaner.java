package io.kestra.jdbc.runner;

import io.kestra.core.utils.ListUtils;
import io.kestra.jdbc.JdbcTableConfig;
import io.kestra.jdbc.JooqDSLContextWrapper;
import io.kestra.jdbc.repository.AbstractJdbcRepository;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Requires;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.Getter;
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
@JdbcRunnerEnabled
@Slf4j
@Requires(property = "kestra.jdbc.cleaner")
public class JdbcCleaner {
    private static final Field<Object> UPDATED_FIELD = AbstractJdbcRepository.field("updated");
    private static final int MYSQL_BATCH_SIZE = 10_000;

    private final JooqDSLContextWrapper dslContextWrapper;
    private final Configuration configuration;
    private final JdbcCleanerService jdbcCleanerService;
    private final Table<Record> queueTable;

    @Inject
    public JdbcCleaner(@Named("queues") JdbcTableConfig jdbcTableConfig,
                       JooqDSLContextWrapper dslContextWrapper,
                       Configuration configuration,
                       JdbcCleanerService jdbcCleanerService
    ) {
        this.dslContextWrapper = dslContextWrapper;
        this.configuration = configuration;
        this.jdbcCleanerService = jdbcCleanerService;

        this.queueTable = DSL.table(jdbcTableConfig.table());
    }

    @Scheduled(initialDelay = "${kestra.jdbc.cleaner.initial-delay}", fixedDelay = "${kestra.jdbc.cleaner.fixed-delay}")
    public long deleteQueue() {
        LongAdder totalDeleted = new LongAdder();
        // first, delete types that are configured more specifically
        ListUtils.emptyOnNull(configuration.getTypes()).forEach(type -> {
            dslContextWrapper.transaction(configuration -> {
                var condition = UPDATED_FIELD.lessOrEqual(period(configuration, type.getRetention()))
                    .and(jdbcCleanerService.buildTypeCondition(type.getType()));
                int deleted = delete(configuration, condition);
                log.info("Cleaned {} records from {} for type {}", deleted, this.queueTable.getName(), type.getType());
                totalDeleted.add(deleted);
            });
        });

        // then, delete all other records
        dslContextWrapper.transaction(configuration -> {
            var condition = UPDATED_FIELD.lessOrEqual(period(configuration, this.configuration.getRetention()));
            int deleted = delete(configuration, condition);
            log.info("Cleaned {} records from {}", deleted, this.queueTable.getName());
            totalDeleted.add(deleted);
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

    @ConfigurationProperties("kestra.jdbc.cleaner")
    @Getter
    public static class Configuration {
        Duration retention;
        List<TypeConfiguration> types;

        @Getter
        @EachProperty(value = "types", list = true)
        public static class TypeConfiguration {
            String type;
            Duration retention;
        }
    }
}
