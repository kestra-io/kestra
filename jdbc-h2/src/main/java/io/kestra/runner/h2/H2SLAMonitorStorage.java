package io.kestra.runner.h2;

import io.kestra.core.models.flows.sla.SLAMonitor;
import io.kestra.jdbc.runner.AbstractJdbcSLAMonitorStorage;
import io.kestra.repository.h2.H2Repository;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@H2QueueEnabled
public class H2SLAMonitorStorage extends AbstractJdbcSLAMonitorStorage {
    public H2SLAMonitorStorage(@Named("slamonitor") H2Repository<SLAMonitor> repository) {
        super(repository);
    }
}
