package io.kestra.runner.postgres;

import io.kestra.core.runners.ExecutionRunning;
import io.kestra.jdbc.runner.AbstractJdbcExecutionRunningStorage;
import io.kestra.repository.postgres.PostgresRepository;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@PostgresQueueEnabled
public class PostgresExecutionRunningStorage extends AbstractJdbcExecutionRunningStorage {
    public PostgresExecutionRunningStorage(@Named("executionrunning") PostgresRepository<ExecutionRunning> repository) {
        super(repository);
    }
}
