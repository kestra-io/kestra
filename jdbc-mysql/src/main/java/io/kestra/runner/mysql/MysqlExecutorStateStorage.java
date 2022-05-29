package io.kestra.runner.mysql;

import io.kestra.jdbc.runner.AbstractExecutorStateStorage;
import io.kestra.jdbc.runner.JdbcExecutorState;
import io.kestra.repository.mysql.MysqlRepository;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Singleton;

@Singleton
@MysqlQueueEnabled
public class MysqlExecutorStateStorage extends AbstractExecutorStateStorage {
    public MysqlExecutorStateStorage(ApplicationContext applicationContext) {
        super(new MysqlRepository<>(JdbcExecutorState.class, applicationContext));
    }
}
