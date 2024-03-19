package io.kestra.runner.postgres;

import io.kestra.core.runners.SubflowExecution;
import io.kestra.jdbc.runner.AbstractJdbcSubflowExecutionStorage;
import io.kestra.repository.postgres.PostgresRepository;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@PostgresQueueEnabled
public class PostgresSubflowExecutionStorage extends AbstractJdbcSubflowExecutionStorage {
    public PostgresSubflowExecutionStorage(@Named("subflow-executions") PostgresRepository<SubflowExecution<?>> repository) {
        super(repository);
    }
}
