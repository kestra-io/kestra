package io.kestra.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sql.DataSource;

import org.jooq.ExecuteContext;
import org.jooq.ExecuteListener;
import org.jooq.ExecuteListenerProvider;

import io.kestra.core.metrics.MetricRegistry;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Factory
public class JooqExecuteListenerFactory {

    @EachBean(DataSource.class)
    public ExecuteListenerProvider jooqConfiguration(MetricRegistry metricRegistry, JdbcTableConfigs.MetricConfig metricConfig) {
        return new ExecuteListenerProvider() {
            @Override
            public @NotNull ExecuteListener provide() {
                return new ExecuteListener() {
                    private static final AtomicBoolean CONNECTION_CHECKED = new AtomicBoolean(false);

                    private long startTime;

                    @Override
                    public void executeStart(ExecuteContext ctx) {
                        startTime = System.nanoTime();

                        // check that isolation level is READ UNCOMMITED, it's the default for Postgres but not for MySQL,
                        // our queue system didn't work correctly otherwise.
                        if (!CONNECTION_CHECKED.getAndSet(true)) {
                            try {
                                if (ctx.connection().getTransactionIsolation() != Connection.TRANSACTION_READ_COMMITTED) {
                                    throw new IllegalStateException("Isolation level must be READ COMMITTED");
                                }
                            } catch (SQLException e) {
                                // silently ignore any exception here
                            }
                        }
                    }

                    @Override
                    public void executeEnd(ExecuteContext ctx) {
                        long duration = System.nanoTime() - startTime;

                        // Record a timer for the sanitized SQL for queries above the threshold.
                        // IN-lists collapsed and aliases/sort columns redacted, so the tag stays bounded.
                        // Exclude batch queries as they will be expanded without parameters.
                        if (duration > metricConfig.queryDurationThresholdMs() * 1_000_000 && ctx.batchMode() != ExecuteContext.BatchMode.MULTIPLE && ctx.sql() != null) {
                            String[] tags = { "sql", JdbcSqlSanitizer.sanitize(ctx.sql()) };
                            metricRegistry.timer(MetricRegistry.METRIC_JDBC_QUERY_DURATION, MetricRegistry.METRIC_JDBC_QUERY_DURATION_DESCRIPTION, tags)
                                .record(duration, TimeUnit.NANOSECONDS);
                        }

                        // logged unsanitized, deliberately: the sanitized tag above loses which column a slow query sorted/filtered by
                        if (log.isTraceEnabled()) {
                            log.trace("[Duration: {}] [Rows: {}] [Query: {}]", duration, ctx.rows(), ctx.query());
                        } else if (log.isDebugEnabled()) {
                            log.debug("[Duration: {}] [Rows: {}] [Query: {}]", duration, ctx.rows(), ctx.sql());
                        }
                    }
                };
            }
        };
    }
}
