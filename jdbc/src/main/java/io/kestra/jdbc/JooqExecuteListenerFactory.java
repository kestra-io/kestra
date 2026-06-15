package io.kestra.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sql.DataSource;

import org.apache.commons.lang3.ArrayUtils;
import org.jooq.ExecuteContext;
import org.jooq.ExecuteListener;

import io.kestra.core.metrics.MetricRegistry;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Factory
public class JooqExecuteListenerFactory {
    @EachBean(DataSource.class)
    public org.jooq.ExecuteListenerProvider jooqConfiguration(MetricRegistry metricRegistry) {
        return new org.jooq.ExecuteListenerProvider() {
            @Override
            public @NotNull ExecuteListener provide() {
                return new ExecuteListener() {
                    private static final AtomicBoolean CONNECTION_CHECKED = new AtomicBoolean(false);
                    private Long startTime;

                    @Override
                    public void executeStart(ExecuteContext ctx) {
                        startTime = System.currentTimeMillis();

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
                        Duration duration = Duration.ofMillis(System.currentTimeMillis() - startTime);

                        String[] tags = tags(ctx);
                        metricRegistry.timer(MetricRegistry.METRIC_JDBC_QUERY_DURATION, MetricRegistry.METRIC_JDBC_QUERY_DURATION_DESCRIPTION, tags)
                            .record(duration);

                        if (log.isTraceEnabled()) {
                            log.trace("[Duration: {}] [Rows: {}] [Query: {}]", duration, ctx.rows(), ctx.query());
                        } else if (log.isDebugEnabled()) {
                            log.debug("[Duration: {}] [Rows: {}] [Query: {}]", duration, ctx.rows(), ctx.sql());
                        }
                    }

                    private String[] tags(ExecuteContext ctx) {
                        var tags = new String[] {"batch", ctx.batchMode().name()};

                        // in batch query, the query will be expanded without parameters, and will lead to overflow of metrics
                        if (ctx.batchMode() != ExecuteContext.BatchMode.MULTIPLE) {
                            return ArrayUtils.addAll(tags, "sql", ctx.sql());
                        }

                        return tags;
                    }
                };
            }
        };
    }
}
