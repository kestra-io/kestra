package io.kestra.repository.postgres;

import io.kestra.core.runners.WorkerHeartbeat;
import io.kestra.jdbc.repository.AbstractJdbcWorkerHeartbeatRepository;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@PostgresRepositoryEnabled
public class PostgresWorkerHeartbeatRepository extends AbstractJdbcWorkerHeartbeatRepository {
    @Inject
    public PostgresWorkerHeartbeatRepository(ApplicationContext applicationContext) {
        super(new PostgresRepository<>(WorkerHeartbeat.class, applicationContext));
    }
}
