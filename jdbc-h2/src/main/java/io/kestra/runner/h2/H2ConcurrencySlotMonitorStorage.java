package io.kestra.runner.h2;

import io.kestra.core.runners.ConcurrencySlotMonitor;
import io.kestra.jdbc.runner.AbstractJdbcConcurrencySlotMonitorStorage;
import io.kestra.repository.h2.H2Repository;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@H2QueueEnabled
public class H2ConcurrencySlotMonitorStorage extends AbstractJdbcConcurrencySlotMonitorStorage {
    public H2ConcurrencySlotMonitorStorage(@Named("concurrencyslotmonitor") H2Repository<ConcurrencySlotMonitor> repository) {
        super(repository);
    }
}
