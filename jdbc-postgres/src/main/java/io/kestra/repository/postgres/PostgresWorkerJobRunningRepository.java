package io.kestra.repository.postgres;

import io.kestra.core.runners.WorkerJobRunning;
import io.kestra.jdbc.repository.AbstractJdbcWorkerJobRunningRepository;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@PostgresRepositoryEnabled
public class PostgresWorkerJobRunningRepository extends AbstractJdbcWorkerJobRunningRepository {
    @Inject
    public PostgresWorkerJobRunningRepository(ApplicationContext applicationContext) {
        super(new PostgresRepository<>(WorkerJobRunning.class, applicationContext));
    }
}
