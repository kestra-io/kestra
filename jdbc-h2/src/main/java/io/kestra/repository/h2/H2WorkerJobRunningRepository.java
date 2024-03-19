package io.kestra.repository.h2;

import io.kestra.core.runners.WorkerJobRunning;
import io.kestra.jdbc.repository.AbstractJdbcWorkerJobRunningRepository;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@H2RepositoryEnabled
public class H2WorkerJobRunningRepository extends AbstractJdbcWorkerJobRunningRepository {
    @Inject
    public H2WorkerJobRunningRepository(@Named("workerjobrunning") H2Repository<WorkerJobRunning> repository) {
        super(repository);
    }
}
