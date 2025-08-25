package io.kestra.runner.mysql;

import io.kestra.core.runners.ExecutionRunning;
import io.kestra.jdbc.runner.AbstractJdbcExecutionRunningStorage;
import io.kestra.repository.mysql.MysqlRepository;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@MysqlQueueEnabled
public class MysqlExecutionRunningStorage extends AbstractJdbcExecutionRunningStorage {
    public MysqlExecutionRunningStorage(@Named("executionrunning") MysqlRepository<ExecutionRunning> repository) {
        super(repository);
    }
}
