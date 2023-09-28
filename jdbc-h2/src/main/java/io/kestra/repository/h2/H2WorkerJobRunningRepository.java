package io.kestra.repository.h2;

import io.kestra.core.runners.WorkerJobRunning;
import io.kestra.jdbc.repository.AbstractJdbcWorkerJobRunningRepository;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@H2RepositoryEnabled
public class H2WorkerJobRunningRepository extends AbstractJdbcWorkerJobRunningRepository {
    @Inject
    public H2WorkerJobRunningRepository(ApplicationContext applicationContext) {
        super(new H2Repository<>(WorkerJobRunning.class, applicationContext));
    }
}
