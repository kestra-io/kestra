package io.kestra.runner.postgres;

import io.kestra.core.runners.WorkerTaskExecution;
import io.kestra.jdbc.runner.AbstractJdbcWorkerTaskExecutionStorage;
import io.kestra.repository.postgres.PostgresRepository;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Singleton;

@Singleton
@PostgresQueueEnabled
public class PostgresWorkerTaskExecutionStorage extends AbstractJdbcWorkerTaskExecutionStorage {
    public PostgresWorkerTaskExecutionStorage(ApplicationContext applicationContext) {
        super(new PostgresRepository<>(WorkerTaskExecution.class, applicationContext));
    }
}
