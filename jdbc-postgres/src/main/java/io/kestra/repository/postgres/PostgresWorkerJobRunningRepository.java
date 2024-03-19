package io.kestra.repository.postgres;

import io.kestra.core.runners.WorkerJobRunning;
import io.kestra.jdbc.repository.AbstractJdbcWorkerJobRunningRepository;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@PostgresRepositoryEnabled
public class PostgresWorkerJobRunningRepository extends AbstractJdbcWorkerJobRunningRepository {
    @Inject
    public PostgresWorkerJobRunningRepository(@Named("workerjobrunning") PostgresRepository<WorkerJobRunning> repository) {
        super(repository);
    }
}
