package io.kestra.runner.postgres;

import io.kestra.core.runners.ExecutionQueued;
import io.kestra.jdbc.runner.AbstractJdbcExecutionQueuedStorage;
import io.kestra.repository.postgres.PostgresRepository;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Singleton;

@Singleton
@PostgresQueueEnabled
public class PostgresExecutionQueuedStorage extends AbstractJdbcExecutionQueuedStorage {
    public PostgresExecutionQueuedStorage(ApplicationContext applicationContext) {
        super(new PostgresRepository<>(ExecutionQueued.class, applicationContext));
    }
}
