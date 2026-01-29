package io.kestra.runner.postgres;

import io.kestra.core.runners.ConcurrencySlotMonitor;
import io.kestra.jdbc.runner.AbstractJdbcConcurrencySlotMonitorStorage;
import io.kestra.repository.postgres.PostgresRepository;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@PostgresQueueEnabled
public class PostgresConcurrencySlotMonitorStorage extends AbstractJdbcConcurrencySlotMonitorStorage {
    public PostgresConcurrencySlotMonitorStorage(@Named("concurrencyslotmonitor") PostgresRepository<ConcurrencySlotMonitor> repository) {
        super(repository);
    }
}
