package io.kestra.runner.mysql;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.Temporal;

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

    @Override
    protected Temporal getNow(Instant now) {
        // 'date' holds a UTC wall clock, matching DATETIME(6) with no zone.
        return LocalDateTime.ofInstant(now, ZoneOffset.UTC);
    }
}
