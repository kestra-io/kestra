package io.kestra.repository.h2;

import io.kestra.core.runners.WorkerJobRunning;
import io.kestra.jdbc.runner.AbstractJdbcWorkerJobRunningStateStore;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@H2RepositoryEnabled
public class H2WorkerJobRunningStateStore extends AbstractJdbcWorkerJobRunningStateStore {
    @Inject
    public H2WorkerJobRunningStateStore(@Named("workerjobrunning") H2Repository<WorkerJobRunning> repository) {
        super(repository);
    }
}
