package io.kestra.runner.mysql;

import io.kestra.core.runners.ConcurrencySlotMonitor;
import io.kestra.jdbc.runner.AbstractJdbcConcurrencySlotMonitorStorage;
import io.kestra.repository.mysql.MysqlRepository;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@MysqlQueueEnabled
public class MysqlConcurrencySlotMonitorStorage extends AbstractJdbcConcurrencySlotMonitorStorage {
    public MysqlConcurrencySlotMonitorStorage(@Named("concurrencyslotmonitor") MysqlRepository<ConcurrencySlotMonitor> repository) {
        super(repository);
    }
}
