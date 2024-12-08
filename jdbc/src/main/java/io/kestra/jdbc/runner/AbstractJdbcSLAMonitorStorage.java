package io.kestra.jdbc.runner;

import io.kestra.core.models.flows.sla.SLAMonitor;
import io.kestra.core.models.flows.sla.SLAMonitorStorage;
import io.kestra.jdbc.repository.AbstractJdbcRepository;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;

import java.time.Instant;
import java.util.Map;
import java.util.function.Consumer;

public abstract class AbstractJdbcSLAMonitorStorage extends AbstractJdbcRepository implements SLAMonitorStorage {
    protected io.kestra.jdbc.AbstractJdbcRepository<SLAMonitor> jdbcRepository;

    protected AbstractJdbcSLAMonitorStorage(io.kestra.jdbc.AbstractJdbcRepository<SLAMonitor> jdbcRepository) {
        this.jdbcRepository = jdbcRepository;
    }

    @Override
    public void save(SLAMonitor slaMonitor) {
        this.jdbcRepository
            .getDslContextWrapper()
            .transaction(configuration -> {
                DSLContext context = DSL.using(configuration);
                Map<Field<Object>, Object> fields = this.jdbcRepository.persistFields(slaMonitor);
                this.jdbcRepository.persist(slaMonitor, context, fields);
            });
    }

    @Override
    public void purge(String executionId) {
        this.jdbcRepository
            .getDslContextWrapper()
            .transaction(configuration -> {
                DSLContext context = DSL.using(configuration);
                context.delete(this.jdbcRepository.getTable())
                    .where(field("execution_id").eq(executionId))
                    .execute();
            });
    }

    @Override
    public void processExpired(Instant date, Consumer<SLAMonitor> consumer) {
        this.jdbcRepository
            .getDslContextWrapper()
            .transaction(configuration -> {
                DSLContext context = DSL.using(configuration);
                var select = context.select()
                    .from(this.jdbcRepository.getTable())
                    .where(field("deadline").lt(date));

                this.jdbcRepository.fetch(select)
                    .forEach(slaMonitor -> {
                        consumer.accept(slaMonitor);
                        jdbcRepository.delete(slaMonitor);
                    });
            });
    }
}
