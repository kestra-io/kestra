package io.kestra.runner.mysql;

import io.kestra.core.models.flows.sla.SLAMonitor;
import io.kestra.jdbc.runner.AbstractJdbcSLAMonitorStorage;
import io.kestra.repository.mysql.MysqlRepository;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@MysqlQueueEnabled
public class MysqlSLAMonitorStorage  extends AbstractJdbcSLAMonitorStorage {
    public MysqlSLAMonitorStorage(@Named("slamonitor") MysqlRepository<SLAMonitor> repository) {
        super(repository);
    }
}
