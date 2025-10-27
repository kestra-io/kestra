package io.kestra.runner.mysql;

import io.kestra.core.runners.ExecutionQueued;
import io.kestra.jdbc.runner.AbstractJdbcExecutionQueuedStateStore;
import io.kestra.repository.mysql.MysqlRepository;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@MysqlQueueEnabled
public class MysqlExecutionQueuedStateStore extends AbstractJdbcExecutionQueuedStateStore {
    public MysqlExecutionQueuedStateStore(@Named("executionqueued") MysqlRepository<ExecutionQueued> repository) {
        super(repository);
    }
}
