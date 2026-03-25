package io.kestra.jdbc.runner;

import java.time.Instant;
import java.time.temporal.Temporal;
import java.util.Map;
import java.util.function.Consumer;

import org.jooq.Field;
import org.jooq.impl.DSL;

import io.kestra.core.runners.ExecutionDelay;
import io.kestra.executor.ExecutionDelayStateStore;
import io.kestra.jdbc.repository.AbstractJdbcRepository;

public abstract class AbstractJdbcExecutionDelayStateStore extends AbstractJdbcRepository implements ExecutionDelayStateStore {
    protected io.kestra.jdbc.AbstractJdbcRepository<ExecutionDelay> jdbcRepository;

    private static final Field<Object> DATE_FIELD = DSL.field(DSL.quotedName("date"));

    public AbstractJdbcExecutionDelayStateStore(io.kestra.jdbc.AbstractJdbcRepository<ExecutionDelay> jdbcRepository) {
        this.jdbcRepository = jdbcRepository;
    }

    @Override
    public void processExpired(Instant now, Consumer<ExecutionDelay> consumer) {
        this.jdbcRepository
            .getDslContextWrapper()
            .transaction(configuration ->
            {
                var select = DSL
                    .using(configuration)
                    .select(VALUE_FIELD)
                    .from(this.jdbcRepository.getTable())
                    .where(DATE_FIELD.lessOrEqual(getNow(now)))
                    .forUpdate()
                    .skipLocked();

                this.jdbcRepository.fetch(select)
                    .forEach(executionDelay ->
                    {
                        consumer.accept(executionDelay);
                        jdbcRepository.delete(executionDelay);
                    });
            });
    }

    protected Temporal getNow(Instant now) {
        return now;
    }

    @Override
    public void save(ExecutionDelay executionDelay) {
        Map<Field<Object>, Object> fields = this.jdbcRepository.persistFields(executionDelay);
        this.jdbcRepository.persist(executionDelay, fields);
    }
}
