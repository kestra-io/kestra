package io.kestra.runner.postgres;

import io.kestra.core.models.flows.sla.SLAMonitor;
import io.kestra.jdbc.runner.AbstractJdbcSLAMonitorStorage;
import io.kestra.repository.postgres.PostgresRepository;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@PostgresQueueEnabled
public class PostgresSLAMonitorStorage  extends AbstractJdbcSLAMonitorStorage {
    public PostgresSLAMonitorStorage(@Named("slamonitor") PostgresRepository<SLAMonitor> repository) {
        super(repository);
    }
}
