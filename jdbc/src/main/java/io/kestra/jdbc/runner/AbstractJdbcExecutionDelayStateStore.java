package io.kestra.jdbc.runner;

import io.kestra.core.runners.ExecutionDelay;
import io.kestra.executor.ExecutionDelayStateStore;
import io.kestra.jdbc.repository.AbstractJdbcRepository;
import org.jooq.Field;
import org.jooq.impl.DSL;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.function.Consumer;

public abstract class AbstractJdbcExecutionDelayStateStore extends AbstractJdbcRepository implements ExecutionDelayStateStore {
    protected io.kestra.jdbc.AbstractJdbcRepository<ExecutionDelay> jdbcRepository;

    public AbstractJdbcExecutionDelayStateStore(io.kestra.jdbc.AbstractJdbcRepository<ExecutionDelay> jdbcRepository) {
        this.jdbcRepository = jdbcRepository;
    }

    @Override
    public void processExpired(Instant now, Consumer<ExecutionDelay> consumer) {
        this.jdbcRepository
            .getDslContextWrapper()
            .transaction(configuration -> {
                var select = DSL
                    .using(configuration)
                    .select(AbstractJdbcRepository.field("value"))
                    .from(this.jdbcRepository.getTable())
                    .where(
                        AbstractJdbcRepository.field("date").lessOrEqual(now)
                    )
                    .forUpdate()
                    .skipLocked();

                this.jdbcRepository.fetch(select)
                    .forEach(executionDelay -> {
                        consumer.accept(executionDelay);
                        jdbcRepository.delete(executionDelay);
                    });
            });
    }

    @Override
    public void save(ExecutionDelay executionDelay) {
        Map<Field<Object>, Object> fields = this.jdbcRepository.persistFields(executionDelay);
        this.jdbcRepository.persist(executionDelay, fields);
    }
}
