package io.kestra.runner.mysql;

import io.kestra.core.runners.ExecutionDelay;
import io.kestra.jdbc.runner.AbstractJdbcExecutionDelayStorage;
import io.kestra.repository.mysql.MysqlRepository;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.Temporal;

@Singleton
@MysqlQueueEnabled
public class MysqlExecutionDelayStorage extends AbstractJdbcExecutionDelayStorage {
    public MysqlExecutionDelayStorage(@Named("executordelayed") MysqlRepository<ExecutionDelay> repository) {
        super(repository);
    }

    @Override
    protected Temporal getNow() {
        // 'date' column in the table is in UTC
        // convert 'now' to UTC LocalDateTime to avoid any timezone/offset interpretation by the database.
        return ZonedDateTime.now().withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }
}
