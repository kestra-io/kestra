package io.kestra.jdbc.runner;

import io.kestra.core.models.executions.Execution;
import io.kestra.jdbc.AbstractJdbcRepository;
import io.kestra.jdbc.repository.AbstractRepository;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.SelectConditionStep;
import org.jooq.impl.DSL;

import java.util.Map;

public abstract class AbstractExecutorStateStorage {
    protected AbstractJdbcRepository<JdbcExecutorState> jdbcRepository;

    public AbstractExecutorStateStorage(AbstractJdbcRepository<JdbcExecutorState> jdbcRepository) {
        this.jdbcRepository = jdbcRepository;
    }

    public JdbcExecutorState get(DSLContext dslContext, Execution execution) {
        SelectConditionStep<Record1<Object>> select = dslContext
            .select(AbstractRepository.field("value"))
            .from(this.jdbcRepository.getTable())
            .where(
                AbstractRepository.field("key").eq(execution.getId())
            );

        return this.jdbcRepository.fetchOne(select)
            .orElse(new JdbcExecutorState(execution.getId()));
    }

    public void save(DSLContext dslContext, JdbcExecutorState jdbcExecutorState) {
        Map<Field<Object>, Object> fields = this.jdbcRepository.persistFields(jdbcExecutorState);
        this.jdbcRepository.persist(jdbcExecutorState, dslContext, fields);
    }

    public void delete(Execution execution) {
        this.jdbcRepository.delete(new JdbcExecutorState(execution.getId()));
    }
}
