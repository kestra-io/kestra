package io.kestra.jdbc.runner;

import io.kestra.core.runners.ConcurrencySlotMonitor;
import io.kestra.core.runners.ConcurrencySlotMonitorStorage;
import io.kestra.jdbc.repository.AbstractJdbcRepository;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;

import java.time.Instant;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Abstract JDBC implementation of ConcurrencySlotMonitorStorage.
 * Handles persistence and expiration processing for concurrency slot monitors.
 */
public abstract class AbstractJdbcConcurrencySlotMonitorStorage extends AbstractJdbcRepository implements ConcurrencySlotMonitorStorage {
    protected io.kestra.jdbc.AbstractJdbcRepository<ConcurrencySlotMonitor> jdbcRepository;

    protected AbstractJdbcConcurrencySlotMonitorStorage(io.kestra.jdbc.AbstractJdbcRepository<ConcurrencySlotMonitor> jdbcRepository) {
        this.jdbcRepository = jdbcRepository;
    }

    @Override
    public void save(ConcurrencySlotMonitor monitor) {
        this.jdbcRepository
            .getDslContextWrapper()
            .transaction(configuration -> {
                DSLContext context = DSL.using(configuration);
                Map<Field<Object>, Object> fields = this.jdbcRepository.persistFields(monitor);
                this.jdbcRepository.persist(monitor, context, fields);
            });
    }

    @Override
    public void delete(String executionId) {
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
    public void processExpired(Instant date, Consumer<ConcurrencySlotMonitor> consumer) {
        this.jdbcRepository
            .getDslContextWrapper()
            .transaction(configuration -> {
                DSLContext context = DSL.using(configuration);
                var select = context.select()
                    .from(this.jdbcRepository.getTable())
                    .where(field("deadline").lt(date))
                    .forUpdate()
                    .skipLocked();

                this.jdbcRepository.fetch(select)
                    .forEach(monitor -> {
                        consumer.accept(monitor);
                        jdbcRepository.delete(monitor);
                    });
            });
    }
}
