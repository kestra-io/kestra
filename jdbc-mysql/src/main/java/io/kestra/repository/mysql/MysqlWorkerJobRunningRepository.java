package io.kestra.repository.mysql;

import io.kestra.core.runners.WorkerJobRunning;
import io.kestra.jdbc.repository.AbstractJdbcWorkerJobRunningRepository;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@MysqlRepositoryEnabled
public class MysqlWorkerJobRunningRepository extends AbstractJdbcWorkerJobRunningRepository {
    @Inject
    public MysqlWorkerJobRunningRepository(@Named("workerjobrunning") MysqlRepository<WorkerJobRunning> repository) {
        super(repository);
    }
}
