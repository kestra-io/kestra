package io.kestra.runner.postgres;

import io.kestra.core.runners.ExecutionDelay;
import io.kestra.jdbc.runner.AbstractJdbcExecutionDelayStorage;
import io.kestra.repository.postgres.PostgresRepository;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Singleton;

@Singleton
@PostgresQueueEnabled
public class PostgresExecutionDelayStorage extends AbstractJdbcExecutionDelayStorage {
    public PostgresExecutionDelayStorage(ApplicationContext applicationContext) {
        super(new PostgresRepository<>(ExecutionDelay.class, applicationContext));
    }
}
