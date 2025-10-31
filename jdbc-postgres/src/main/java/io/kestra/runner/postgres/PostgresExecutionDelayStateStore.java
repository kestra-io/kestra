package io.kestra.runner.postgres;

import io.kestra.core.runners.ExecutionDelay;
import io.kestra.jdbc.runner.AbstractJdbcExecutionDelayStateStore;
import io.kestra.repository.postgres.PostgresRepository;
import io.kestra.repository.postgres.PostgresRepositoryEnabled;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@PostgresRepositoryEnabled
public class PostgresExecutionDelayStateStore extends AbstractJdbcExecutionDelayStateStore {
    public PostgresExecutionDelayStateStore(@Named("executordelayed") PostgresRepository<ExecutionDelay> repository) {
        super(repository);
    }
}
