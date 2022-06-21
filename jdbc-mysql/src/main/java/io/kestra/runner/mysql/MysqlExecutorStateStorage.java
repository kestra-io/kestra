package io.kestra.runner.mysql;

import io.kestra.jdbc.runner.AbstractJdbcExecutorStateStorage;
import io.kestra.core.runners.ExecutorState;
import io.kestra.repository.mysql.MysqlRepository;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Singleton;

@Singleton
@MysqlQueueEnabled
public class MysqlExecutorStateStorage extends AbstractJdbcExecutorStateStorage {
    public MysqlExecutorStateStorage(ApplicationContext applicationContext) {
        super(new MysqlRepository<>(ExecutorState.class, applicationContext));
    }
}
