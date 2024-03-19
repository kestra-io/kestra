package io.kestra.repository.h2;

import io.kestra.core.models.executions.Execution;
import io.kestra.jdbc.repository.AbstractJdbcExecutionRepository;
import io.kestra.jdbc.runner.AbstractJdbcExecutorStateStorage;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jooq.Condition;

import java.util.Map;

@Singleton
@H2RepositoryEnabled
public class H2ExecutionRepository extends AbstractJdbcExecutionRepository {
    @Inject
    public H2ExecutionRepository(@Named("executions") H2Repository<Execution> repository,
                                 ApplicationContext applicationContext,
                                 AbstractJdbcExecutorStateStorage executorStateStorage) {
        super(repository, applicationContext, executorStateStorage);
    }

    @Override
    protected Condition findCondition(String query, Map<String, String> labels) {
        return H2ExecutionRepositoryService.findCondition(this.jdbcRepository, query, labels);
    }
}
