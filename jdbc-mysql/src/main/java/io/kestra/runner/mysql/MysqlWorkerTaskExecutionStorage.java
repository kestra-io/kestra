package io.kestra.runner.mysql;

import io.kestra.core.runners.WorkerTaskExecution;
import io.kestra.jdbc.runner.AbstractJdbcWorkerTaskExecutionStorage;
import io.kestra.repository.mysql.MysqlRepository;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Singleton;

@Singleton
@MysqlQueueEnabled
public class MysqlWorkerTaskExecutionStorage extends AbstractJdbcWorkerTaskExecutionStorage {
    public MysqlWorkerTaskExecutionStorage(ApplicationContext applicationContext) {
        super(new MysqlRepository<>(WorkerTaskExecution.class, applicationContext));
    }
}
