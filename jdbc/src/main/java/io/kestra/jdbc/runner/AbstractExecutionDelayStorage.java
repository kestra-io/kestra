package io.kestra.jdbc.runner;

import io.kestra.core.runners.ExecutionDelay;
import io.kestra.jdbc.AbstractJdbcRepository;
import io.kestra.jdbc.repository.AbstractRepository;
import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.SelectConditionStep;
import org.jooq.impl.DSL;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.function.Consumer;

public abstract class AbstractExecutionDelayStorage extends AbstractRepository {
    protected AbstractJdbcRepository<ExecutionDelay> jdbcRepository;

    public AbstractExecutionDelayStorage(AbstractJdbcRepository<ExecutionDelay> jdbcRepository) {
        this.jdbcRepository = jdbcRepository;
    }

    public void get(Consumer<ExecutionDelay> consumer) {
        ZonedDateTime now = ZonedDateTime.now();

        this.jdbcRepository
            .getDslContextWrapper()
            .transaction(configuration -> {
                SelectConditionStep<Record1<Object>> select = DSL
                    .using(configuration)
                    .select(AbstractRepository.field("value"))
                    .from(this.jdbcRepository.getTable())
                    .where(
                        AbstractRepository.field("date").lessOrEqual(now.toOffsetDateTime())
                    );

                this.jdbcRepository.fetch(select)
                    .forEach(executionDelay -> {
                        consumer.accept(executionDelay);
                        jdbcRepository.delete(executionDelay);
                    });
            });
    }

    public void save(ExecutionDelay executionDelay) {
        Map<Field<Object>, Object> fields = this.jdbcRepository.persistFields(executionDelay);
        this.jdbcRepository.persist(executionDelay, fields);
    }
}
