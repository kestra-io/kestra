package io.kestra.repository.mysql;

import io.kestra.core.runners.WorkerJobRunning;
import io.kestra.jdbc.runner.AbstractJdbcWorkerJobRunningStateStore;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@MysqlRepositoryEnabled
public class MysqlWorkerJobRunningStateStore extends AbstractJdbcWorkerJobRunningStateStore {
    @Inject
    public MysqlWorkerJobRunningStateStore(@Named("workerjobrunning") MysqlRepository<WorkerJobRunning> repository) {
        super(repository);
    }
}
