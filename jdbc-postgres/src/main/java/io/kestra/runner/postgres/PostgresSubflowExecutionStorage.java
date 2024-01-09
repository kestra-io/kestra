package io.kestra.runner.postgres;

import io.kestra.core.runners.SubflowExecution;
import io.kestra.jdbc.runner.AbstractJdbcSubflowExecutionStorage;
import io.kestra.repository.postgres.PostgresRepository;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Singleton;

@Singleton
@PostgresQueueEnabled
public class PostgresSubflowExecutionStorage extends AbstractJdbcSubflowExecutionStorage {
    public PostgresSubflowExecutionStorage(ApplicationContext applicationContext) {
        super(new PostgresRepository<>(SubflowExecution.class, applicationContext));
    }
}
