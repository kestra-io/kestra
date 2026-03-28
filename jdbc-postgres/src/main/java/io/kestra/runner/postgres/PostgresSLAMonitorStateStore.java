package io.kestra.runner.postgres;

import io.kestra.core.models.flows.sla.SLAMonitor;
import io.kestra.jdbc.runner.AbstractJdbcSLAMonitorStateStore;
import io.kestra.repository.postgres.PostgresRepository;
import io.kestra.repository.postgres.PostgresRepositoryEnabled;

import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@PostgresRepositoryEnabled
public class PostgresSLAMonitorStateStore extends AbstractJdbcSLAMonitorStateStore {
    public PostgresSLAMonitorStateStore(@Named("slamonitor") PostgresRepository<SLAMonitor> repository) {
        super(repository);
    }
}
