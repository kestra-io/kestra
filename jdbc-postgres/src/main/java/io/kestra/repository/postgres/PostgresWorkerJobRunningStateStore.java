package io.kestra.repository.postgres;

import io.kestra.core.runners.WorkerJobRunning;
import io.kestra.jdbc.runner.AbstractJdbcWorkerJobRunningStateStore;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@PostgresRepositoryEnabled
public class PostgresWorkerJobRunningStateStore extends AbstractJdbcWorkerJobRunningStateStore {
    @Inject
    public PostgresWorkerJobRunningStateStore(@Named("workerjobrunning") PostgresRepository<WorkerJobRunning> repository) {
        super(repository);
    }
}
