package io.kestra.repository.mysql;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.jdbc.repository.AbstractExecutionRepository;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@MysqlRepositoryEnabled
public class MysqlExecutionRepository extends AbstractExecutionRepository implements ExecutionRepositoryInterface {
    @Inject
    public MysqlExecutionRepository(ApplicationContext applicationContext) {
        super(new MysqlRepository<>(Execution.class, applicationContext), applicationContext);
    }
}
