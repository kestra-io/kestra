package io.kestra.runner.mysql;

import io.kestra.core.runners.ExecutionQueued;
import io.kestra.jdbc.runner.AbstractJdbcExecutionQueuedStateStore;
import io.kestra.repository.mysql.MysqlRepository;
import io.kestra.repository.mysql.MysqlRepositoryEnabled;

import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@MysqlRepositoryEnabled
public class MysqlExecutionQueuedStateStore extends AbstractJdbcExecutionQueuedStateStore {
    public MysqlExecutionQueuedStateStore(@Named("executionqueued") MysqlRepository<ExecutionQueued> repository) {
        super(repository);
    }
}
