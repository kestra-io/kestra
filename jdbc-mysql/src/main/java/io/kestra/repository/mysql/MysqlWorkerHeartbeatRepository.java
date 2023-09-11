package io.kestra.repository.mysql;

import io.kestra.core.runners.WorkerHeartbeat;
import io.kestra.jdbc.repository.AbstractJdbcWorkerHeartbeatRepository;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@MysqlRepositoryEnabled
public class MysqlWorkerHeartbeatRepository extends AbstractJdbcWorkerHeartbeatRepository {
    @Inject
    public MysqlWorkerHeartbeatRepository(ApplicationContext applicationContext) {
        super(new MysqlRepository<>(WorkerHeartbeat.class, applicationContext));
    }
}
