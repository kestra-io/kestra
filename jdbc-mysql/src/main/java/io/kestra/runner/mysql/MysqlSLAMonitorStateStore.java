package io.kestra.runner.mysql;

import io.kestra.core.models.flows.sla.SLAMonitor;
import io.kestra.jdbc.runner.AbstractJdbcSLAMonitorStateStore;
import io.kestra.repository.mysql.MysqlRepository;
import io.kestra.repository.mysql.MysqlRepositoryEnabled;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@MysqlRepositoryEnabled
public class MysqlSLAMonitorStateStore extends AbstractJdbcSLAMonitorStateStore {
    public MysqlSLAMonitorStateStore(@Named("slamonitor") MysqlRepository<SLAMonitor> repository) {
        super(repository);
    }
}
