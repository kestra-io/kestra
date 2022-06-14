package io.kestra.runner.postgres;

import io.kestra.jdbc.runner.AbstractJdbcExecutorStateStorage;
import io.kestra.core.runners.ExecutorState;
import io.kestra.repository.postgres.PostgresRepository;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Singleton;

@Singleton
@PostgresQueueEnabled
public class PostgresExecutorStateStorage extends AbstractJdbcExecutorStateStorage {
    public PostgresExecutorStateStorage(ApplicationContext applicationContext) {
        super(new PostgresRepository<>(ExecutorState.class, applicationContext));
    }
}
