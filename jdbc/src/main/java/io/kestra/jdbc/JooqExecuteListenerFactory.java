package io.kestra.jdbc;

import io.kestra.core.metrics.MetricRegistry;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import lombok.extern.slf4j.Slf4j;
import org.jooq.ExecuteContext;
import org.jooq.ExecuteListener;

import java.time.Duration;
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

                        metricRegistry.timer(MetricRegistry.JDBC_QUERY_DURATION, "sql", ctx.sql())
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
