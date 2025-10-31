package io.kestra.runner.mysql;

import io.kestra.core.runners.ExecutionDelay;
import io.kestra.jdbc.runner.AbstractJdbcExecutionDelayStateStore;
import io.kestra.repository.mysql.MysqlRepository;
import io.kestra.repository.mysql.MysqlRepositoryEnabled;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@MysqlRepositoryEnabled
public class MysqlExecutionDelayStateStore extends AbstractJdbcExecutionDelayStateStore {
    public MysqlExecutionDelayStateStore(@Named("executordelayed") MysqlRepository<ExecutionDelay> repository) {
        super(repository);
    }
}
