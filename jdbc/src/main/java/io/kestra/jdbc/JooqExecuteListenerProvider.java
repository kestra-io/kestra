package io.kestra.jdbc;

import io.kestra.core.metrics.MetricRegistry;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.jooq.ExecuteContext;
import org.jooq.ExecuteListener;
import org.jooq.impl.DefaultExecuteListener;

import java.time.Duration;
import javax.validation.constraints.NotNull;

@Singleton
@Named("default")
@Slf4j
public class JooqExecuteListenerProvider implements org.jooq.ExecuteListenerProvider {
    @Inject
    MetricRegistry metricRegistry;

    @Override
    public @NotNull ExecuteListener provide() {
        return new DefaultExecuteListener() {
            Long startTime;

            @Override
            public void executeStart(ExecuteContext ctx) {
                super.executeStart(ctx);
                startTime = System.currentTimeMillis();
            }

            @Override
            public void executeEnd(ExecuteContext ctx) {
                super.executeEnd(ctx);

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
}
