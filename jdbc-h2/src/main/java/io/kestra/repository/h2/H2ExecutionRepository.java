package io.kestra.repository.h2;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.jdbc.repository.AbstractJdbcExecutionRepository;
import io.kestra.jdbc.runner.AbstractJdbcExecutorStateStorage;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jooq.Condition;

import java.util.List;

@Singleton
@H2RepositoryEnabled
public class H2ExecutionRepository extends AbstractJdbcExecutionRepository implements ExecutionRepositoryInterface {
    @Inject
    public H2ExecutionRepository(ApplicationContext applicationContext, AbstractJdbcExecutorStateStorage executorStateStorage) {
        super(new H2Repository<>(Execution.class, applicationContext), applicationContext, executorStateStorage);
    }

    @Override
    protected Condition findCondition(String query) {
        return this.jdbcRepository.fullTextCondition(List.of("fulltext"), query);
    }
}
