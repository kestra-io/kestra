package io.kestra.jdbc;

import io.kestra.core.metrics.MetricRegistry;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import lombok.extern.slf4j.Slf4j;
import org.jooq.ExecuteContext;
import org.jooq.ExecuteListener;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import jakarta.validation.constraints.NotNull;

@Slf4j
@Factory
public class JooqExecuteListenerFactory {
    @EachBean(DataSource.class)
    public org.jooq.ExecuteListenerProvider jooqConfiguration(MetricRegistry metricRegistry) {
        return new org.jooq.ExecuteListenerProvider() {
            @Override
            public @NotNull ExecuteListener provide() {
                return new ExecuteListener() {
                    Long startTime;

                    @Override
                    public void executeStart(ExecuteContext ctx) {
                        startTime = System.currentTimeMillis();
                    }

                    @Override
                    public void executeEnd(ExecuteContext ctx) {
                        Duration duration = Duration.ofMillis(System.currentTimeMillis() - startTime);

                        List<String> tags = new ArrayList<>();
                        tags.add("batch");
                        tags.add(ctx.batchMode().name());

                        // in batch query, the query will be expanded without parameters, and will lead to overflow of metrics
                        if (ctx.batchMode() != ExecuteContext.BatchMode.MULTIPLE) {
                            tags.add("sql");
                            tags.add(ctx.sql());
                        }

                        metricRegistry.timer(MetricRegistry.JDBC_QUERY_DURATION, tags.toArray(new String[0]))
                            .record(duration);

                        if (log.isTraceEnabled()) {
                            log.trace("[Duration: {}] [Rows: {}] [Query: {}]", duration, ctx.rows() , ctx.query().toString());
                        } else if (log.isDebugEnabled()) {
                            log.debug("[Duration: {}] [Rows: {}] [Query: {}]", duration, ctx.rows() , ctx.sql());
                        }
                    }
                };
            }
        };
    }
}
