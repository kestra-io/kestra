package io.kestra.repository.h2;

import io.kestra.core.runners.WorkerHeartbeat;
import io.kestra.jdbc.repository.AbstractJdbcWorkerHeartbeatRepository;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@H2RepositoryEnabled
public class H2WorkerHeartbeatRepository extends AbstractJdbcWorkerHeartbeatRepository {
    @Inject
    public H2WorkerHeartbeatRepository(ApplicationContext applicationContext) {
        super(new H2Repository<>(WorkerHeartbeat.class, applicationContext));
    }
}
