package io.kestra.runner.postgres;

import io.kestra.jdbc.runner.AbstractExecutorStateStorage;
import io.kestra.jdbc.runner.JdbcExecutorState;
import io.kestra.repository.postgres.PostgresRepository;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Singleton;

@Singleton
@PostgresQueueEnabled
public class PostgresExecutorStateStorage extends AbstractExecutorStateStorage {
    public PostgresExecutorStateStorage(ApplicationContext applicationContext) {
        super(new PostgresRepository<>(JdbcExecutorState.class, applicationContext));
    }
}
