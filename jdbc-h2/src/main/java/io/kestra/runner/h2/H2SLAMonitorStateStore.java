package io.kestra.runner.h2;

import io.kestra.core.models.flows.sla.SLAMonitor;
import io.kestra.jdbc.runner.AbstractJdbcSLAMonitorStateStore;
import io.kestra.repository.h2.H2Repository;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@H2QueueEnabled
public class H2SLAMonitorStateStore extends AbstractJdbcSLAMonitorStateStore {
    public H2SLAMonitorStateStore(@Named("slamonitor") H2Repository<SLAMonitor> repository) {
        super(repository);
    }
}
