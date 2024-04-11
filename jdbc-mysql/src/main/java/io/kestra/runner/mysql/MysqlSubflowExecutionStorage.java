package io.kestra.runner.mysql;

import io.kestra.core.runners.SubflowExecution;
import io.kestra.jdbc.runner.AbstractJdbcSubflowExecutionStorage;
import io.kestra.repository.mysql.MysqlRepository;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@MysqlQueueEnabled
public class MysqlSubflowExecutionStorage extends AbstractJdbcSubflowExecutionStorage {
    public MysqlSubflowExecutionStorage(@Named("subflow-executions") MysqlRepository<SubflowExecution<?>> repository) {
        super(repository);
    }
}
