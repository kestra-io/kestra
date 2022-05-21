package io.kestra.repository.postgres;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.jdbc.repository.AbstractExecutionRepository;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@PostgresRepositoryEnabled
public class PostgresExecutionRepository extends AbstractExecutionRepository implements ExecutionRepositoryInterface {
    @Inject
    public PostgresExecutionRepository(ApplicationContext applicationContext) {
        super(new PostgresRepository<>(Execution.class, applicationContext), applicationContext);
    }
}
