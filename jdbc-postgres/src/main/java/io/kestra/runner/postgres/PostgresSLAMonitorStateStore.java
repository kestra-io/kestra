package io.kestra.runner.postgres;

import io.kestra.core.models.flows.sla.SLAMonitor;
import io.kestra.jdbc.runner.AbstractJdbcSLAMonitorStateStore;
import io.kestra.repository.postgres.PostgresRepository;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@PostgresQueueEnabled
public class PostgresSLAMonitorStateStore extends AbstractJdbcSLAMonitorStateStore {
    public PostgresSLAMonitorStateStore(@Named("slamonitor") PostgresRepository<SLAMonitor> repository) {
        super(repository);
    }
}
