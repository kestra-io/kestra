package io.kestra.runner.mysql;

import io.kestra.core.runners.WorkerTaskExecution;
import io.kestra.jdbc.runner.AbstractWorkerTaskExecutionStorage;
import io.kestra.repository.mysql.MysqlRepository;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Singleton;

@Singleton
@MysqlQueueEnabled
public class MysqlWorkerTaskExecutionStorage extends AbstractWorkerTaskExecutionStorage {
    public MysqlWorkerTaskExecutionStorage(ApplicationContext applicationContext) {
        super(new MysqlRepository<>(WorkerTaskExecution.class, applicationContext));
    }
}
