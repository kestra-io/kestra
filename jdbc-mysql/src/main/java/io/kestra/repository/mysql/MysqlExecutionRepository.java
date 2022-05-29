package io.kestra.repository.mysql;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.jdbc.repository.AbstractExecutionRepository;
import io.kestra.jdbc.runner.AbstractExecutorStateStorage;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jooq.Condition;

import java.util.Arrays;

@Singleton
@MysqlRepositoryEnabled
public class MysqlExecutionRepository extends AbstractExecutionRepository implements ExecutionRepositoryInterface {
    @Inject
    public MysqlExecutionRepository(ApplicationContext applicationContext, AbstractExecutorStateStorage executorStateStorage) {
        super(new MysqlRepository<>(Execution.class, applicationContext), executorStateStorage);
    }

    @Override
    protected Condition findCondition(String query) {
        return this.jdbcRepository.fullTextCondition(Arrays.asList("namespace", "id"), query);
    }
}
