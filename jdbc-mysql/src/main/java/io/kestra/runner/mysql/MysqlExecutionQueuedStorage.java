package io.kestra.runner.mysql;

import io.kestra.core.runners.ExecutionQueued;
import io.kestra.jdbc.runner.AbstractJdbcExecutionQueuedStorage;
import io.kestra.repository.mysql.MysqlRepository;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Singleton;

@Singleton
@MysqlQueueEnabled
public class MysqlExecutionQueuedStorage extends AbstractJdbcExecutionQueuedStorage {
    public MysqlExecutionQueuedStorage(ApplicationContext applicationContext) {
        super(new MysqlRepository<>(ExecutionQueued.class, applicationContext));
    }
}
