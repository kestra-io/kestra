package io.kestra.jdbc.runner;

import io.kestra.core.runners.ExecutionDelay;
import io.kestra.jdbc.repository.AbstractJdbcRepository;
import org.jooq.Field;
import org.jooq.impl.DSL;

import java.time.ZonedDateTime;
import java.time.temporal.Temporal;
import java.util.Map;
import java.util.function.Consumer;

public abstract class AbstractJdbcExecutionDelayStorage extends AbstractJdbcRepository {
    protected io.kestra.jdbc.AbstractJdbcRepository<ExecutionDelay> jdbcRepository;

    private static final Field<Object> DATE_FIELD = DSL.field(DSL.quotedName("date"));

    public AbstractJdbcExecutionDelayStorage(io.kestra.jdbc.AbstractJdbcRepository<ExecutionDelay> jdbcRepository) {
        this.jdbcRepository = jdbcRepository;
    }

    public void get(Consumer<ExecutionDelay> consumer) {
        this.jdbcRepository
            .getDslContextWrapper()
            .transaction(configuration -> {
                var select = DSL
                    .using(configuration)
                    .select(VALUE_FIELD)
                    .from(this.jdbcRepository.getTable())
                    .where(DATE_FIELD.lessOrEqual(getNow()))
                    .forUpdate()
                    .skipLocked();

                this.jdbcRepository.fetch(select)
                    .forEach(executionDelay -> {
                        consumer.accept(executionDelay);
                        jdbcRepository.delete(executionDelay);
                    });
            });
    }

    protected Temporal getNow() {
        return ZonedDateTime.now().toOffsetDateTime();
    }

    public void save(ExecutionDelay executionDelay) {
        Map<Field<Object>, Object> fields = this.jdbcRepository.persistFields(executionDelay);
        this.jdbcRepository.persist(executionDelay, fields);
    }
}
