package io.kestra.jdbc.runner;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.Temporal;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.LongAdder;

import org.jooq.*;
import org.jooq.Record;
import org.jooq.impl.DSL;

import io.kestra.core.utils.ListUtils;
import io.kestra.jdbc.JdbcTableConfig;
import io.kestra.jdbc.JooqDSLContextWrapper;
import io.kestra.jdbc.repository.AbstractJdbcRepository;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Purges expired records from the <code>queues</code> table based on the configured retention.
 * <p>
 * This bean is intentionally <b>not</b> gated by <code>kestra.server-type</code> so the purge logic can
 * be reused both by the scheduled {@link JdbcCleaner} (running on the executor) and by the
 * <code>sys purge-queue</code> CLI command, which does not start any server component.
 * <p>
 * For MySQL the delete is executed in batches, each committed in its own transaction. Committing per
 * batch is what allows the purge to grind through millions of rows: a single large transaction holds
 * row locks on every deleted row until it commits, which trips <code>innodb_lock_wait_timeout</code> on
 * big tables.
 */
@Singleton
@JdbcRunnerEnabled
@Slf4j
@Requires(property = "kestra.jdbc.cleaner")
public class JdbcQueueCleaner {
    private static final Field<Object> UPDATED_FIELD = AbstractJdbcRepository.field("updated");
    private static final int MYSQL_BATCH_SIZE = 10_000;

    private final JooqDSLContextWrapper dslContextWrapper;
    private final Configuration configuration;
    private final JdbcCleanerService jdbcCleanerService;
    private final Table<Record> queueTable;

    @Inject
    public JdbcQueueCleaner(@Named("queues") JdbcTableConfig jdbcTableConfig,
        JooqDSLContextWrapper dslContextWrapper,
        Configuration configuration,
        JdbcCleanerService jdbcCleanerService) {
        this.dslContextWrapper = Objects.requireNonNull(dslContextWrapper, "dslContextWrapper must not be null");
        this.configuration = Objects.requireNonNull(configuration, "configuration must not be null");
        this.jdbcCleanerService = Objects.requireNonNull(jdbcCleanerService, "jdbcCleanerService must not be null");

        this.queueTable = DSL.table(jdbcTableConfig.table());
    }

    /**
     * Purge the queues table using the configured retention (global and per-type).
     *
     * @return the number of purged records
     */
    public long purge() {
        return purge(null);
    }

    /**
     * Purge the queues table. Per-type retentions always come from the configuration; the global
     * retention can be overridden for a one-off cleanup.
     *
     * @param globalRetentionOverride the retention to use for the global catch-all phase, or
     *                                <code>null</code> to use the configured global retention
     * @return the number of purged records
     */
    public long purge(Duration globalRetentionOverride) {
        LongAdder totalDeleted = new LongAdder();

        // first, delete types that are configured more specifically
        ListUtils.emptyOnNull(configuration.getTypes()).forEach(type -> {
            int deleted = delete(type.getRetention(), jdbcCleanerService.buildTypeCondition(type.getType()));
            log.info("Purged {} records from {} for type {}", deleted, this.queueTable.getName(), type.getType());
            totalDeleted.add(deleted);
        });

        // then, delete all other records using the (possibly overridden) global retention
        Duration globalRetention = globalRetentionOverride != null ? globalRetentionOverride : configuration.getRetention();
        int deleted = delete(globalRetention, null);
        log.info("Purged {} records from {}", deleted, this.queueTable.getName());
        totalDeleted.add(deleted);

        return totalDeleted.longValue();
    }

    private int delete(Duration retention, Condition extraCondition) {
        // MySQL struggles with large transactions so we delete in batches, each batch committed in its own
        // transaction to avoid holding row locks until the whole purge completes. Other dialects delete in a
        // single statement (the loop then simply runs once). Committing per batch is what lets the purge grind
        // through millions of rows without tripping innodb_lock_wait_timeout.
        int totalDeleted = 0;
        int subDeleted;
        do {
            subDeleted = dslContextWrapper.transactionResult(configuration -> {
                var delete = DSL.using(configuration)
                    .delete(this.queueTable)
                    .where(condition(configuration, retention, extraCondition));

                if (configuration.dialect().family() == SQLDialect.MYSQL) {
                    return delete.limit(MYSQL_BATCH_SIZE).execute();
                }
                return delete.execute();
            });
            totalDeleted += subDeleted;
            if (subDeleted > 0) {
                log.debug("Purged a batch of {} records from {} ({} so far)", subDeleted, this.queueTable.getName(), totalDeleted);
            }
            // Only MySQL is batched: a full batch means there may be more, so keep going.
        } while (subDeleted == MYSQL_BATCH_SIZE);

        return totalDeleted;
    }

    private Condition condition(org.jooq.Configuration configuration, Duration retention, Condition extraCondition) {
        Condition condition = UPDATED_FIELD.lessOrEqual(period(configuration, retention));
        return extraCondition != null ? condition.and(extraCondition) : condition;
    }

    private Temporal period(org.jooq.Configuration configuration, Duration retention) {
        if (configuration.dialect().family() == SQLDialect.MYSQL) {
            // 'updated' column in the table is in local time for MySQL
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
