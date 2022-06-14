package io.kestra.runner.h2;

import io.kestra.core.runners.WorkerTaskExecution;
import io.kestra.jdbc.runner.AbstractJdbcWorkerTaskExecutionStorage;
import io.kestra.repository.h2.H2Repository;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Singleton;

@Singleton
@H2QueueEnabled
public class H2WorkerTaskExecutionStorage extends AbstractJdbcWorkerTaskExecutionStorage {
    public H2WorkerTaskExecutionStorage(ApplicationContext applicationContext) {
        super(new H2Repository<>(WorkerTaskExecution.class, applicationContext));
    }
}
