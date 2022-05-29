package io.kestra.repository.postgres;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.jdbc.repository.AbstractExecutionRepository;
import io.kestra.jdbc.runner.AbstractExecutorStateStorage;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jooq.Condition;

import java.util.Collections;

@Singleton
@PostgresRepositoryEnabled
public class PostgresExecutionRepository extends AbstractExecutionRepository implements ExecutionRepositoryInterface {
    @Inject
    public PostgresExecutionRepository(ApplicationContext applicationContext, AbstractExecutorStateStorage executorStateStorage) {
        super(new PostgresRepository<>(Execution.class, applicationContext), executorStateStorage);
    }

    @Override
    protected Condition findCondition(String query) {
        return this.jdbcRepository.fullTextCondition(Collections.singletonList("fulltext"), query);
    }
}
