package io.kestra.runner.mysql;

import io.kestra.core.runners.ExecutionDelay;
import io.kestra.jdbc.runner.AbstractExecutionDelayStorage;
import io.kestra.repository.mysql.MysqlRepository;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Singleton;

@Singleton
@MysqlQueueEnabled
public class MysqlExecutionDelayStorage extends AbstractExecutionDelayStorage {
    public MysqlExecutionDelayStorage(ApplicationContext applicationContext) {
        super(new MysqlRepository<>(ExecutionDelay.class, applicationContext));
    }
}
