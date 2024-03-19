package io.kestra.runner.postgres;

import io.kestra.core.runners.ExecutionQueued;
import io.kestra.jdbc.runner.AbstractJdbcExecutionQueuedStorage;
import io.kestra.repository.postgres.PostgresRepository;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@PostgresQueueEnabled
public class PostgresExecutionQueuedStorage extends AbstractJdbcExecutionQueuedStorage {
    public PostgresExecutionQueuedStorage(@Named("executionqueued") PostgresRepository<ExecutionQueued> repository) {
        super(repository);
    }
}
