package io.kestra.runner.mysql;

import io.kestra.core.runners.ExecutionDelay;
import io.kestra.jdbc.runner.AbstractJdbcExecutionDelayStorage;
import io.kestra.repository.mysql.MysqlRepository;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Singleton;

@Singleton
@MysqlQueueEnabled
public class MysqlExecutionDelayStorage extends AbstractJdbcExecutionDelayStorage {
    public MysqlExecutionDelayStorage(ApplicationContext applicationContext) {
        super(new MysqlRepository<>(ExecutionDelay.class, applicationContext));
    }
}
