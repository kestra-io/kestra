package io.kestra.runner.postgres;

import io.kestra.core.runners.ExecutionDelay;
import io.kestra.jdbc.runner.AbstractJdbcExecutionDelayStorage;
import io.kestra.repository.postgres.PostgresRepository;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@PostgresQueueEnabled
public class PostgresExecutionDelayStorage extends AbstractJdbcExecutionDelayStorage {
    public PostgresExecutionDelayStorage(@Named("executordelayed") PostgresRepository<ExecutionDelay> repository) {
        super(repository);
    }
}
