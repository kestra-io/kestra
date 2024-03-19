package io.kestra.runner.postgres;

import io.kestra.core.runners.ExecutorState;
import io.kestra.jdbc.runner.AbstractJdbcExecutorStateStorage;
import io.kestra.repository.postgres.PostgresRepository;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@PostgresQueueEnabled
public class PostgresExecutorStateStorage extends AbstractJdbcExecutorStateStorage {
    public PostgresExecutorStateStorage(@Named("executorstate") PostgresRepository<ExecutorState> repository) {
        super(repository);
    }
}
