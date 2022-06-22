package io.kestra.repository.postgres;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.jdbc.repository.AbstractJdbcExecutionRepository;
import io.kestra.jdbc.runner.AbstractJdbcExecutorStateStorage;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jooq.Condition;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Singleton
@PostgresRepositoryEnabled
public class PostgresExecutionRepository extends AbstractJdbcExecutionRepository implements ExecutionRepositoryInterface {
    @Inject
    public PostgresExecutionRepository(ApplicationContext applicationContext, AbstractJdbcExecutorStateStorage executorStateStorage) {
        super(new PostgresRepository<>(Execution.class, applicationContext), executorStateStorage);
    }

    @Override
    protected Condition statesFilter(List<State.Type> state) {
        return DSL.or(state
            .stream()
            .map(Enum::name)
            .map(s -> DSL.field("state_current")
                .eq(DSL.field("CAST(? AS state_type)", SQLDataType.VARCHAR(50).getArrayType(), s)
                ))
            .collect(Collectors.toList())
        );
    }

    @Override
    protected Condition findCondition(String query) {
        return this.jdbcRepository.fullTextCondition(Collections.singletonList("fulltext"), query);
    }
}
