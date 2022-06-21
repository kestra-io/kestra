package io.kestra.jdbc.runner;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.runners.ExecutorState;
import io.kestra.jdbc.repository.AbstractJdbcRepository;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.SelectConditionStep;

import java.util.Map;

public abstract class AbstractJdbcExecutorStateStorage {
    protected io.kestra.jdbc.AbstractJdbcRepository<ExecutorState> jdbcRepository;

    public AbstractJdbcExecutorStateStorage(io.kestra.jdbc.AbstractJdbcRepository<ExecutorState> jdbcRepository) {
        this.jdbcRepository = jdbcRepository;
    }

    public ExecutorState get(DSLContext dslContext, Execution execution) {
        SelectConditionStep<Record1<Object>> select = dslContext
            .select(AbstractJdbcRepository.field("value"))
            .from(this.jdbcRepository.getTable())
            .where(
                AbstractJdbcRepository.field("key").eq(execution.getId())
            );

        return this.jdbcRepository.fetchOne(select)
            .orElse(new ExecutorState(execution.getId()));
    }

    public void save(DSLContext dslContext, ExecutorState executorState) {
        Map<Field<Object>, Object> fields = this.jdbcRepository.persistFields(executorState);
        this.jdbcRepository.persist(executorState, dslContext, fields);
    }

    public void delete(Execution execution) {
        this.jdbcRepository.delete(new ExecutorState(execution.getId()));
    }
}
