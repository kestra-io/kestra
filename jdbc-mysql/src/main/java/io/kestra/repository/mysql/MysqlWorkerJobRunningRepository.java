package io.kestra.repository.mysql;

import io.kestra.core.runners.WorkerJobRunning;
import io.kestra.jdbc.repository.AbstractJdbcWorkerJobRunningRepository;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@MysqlRepositoryEnabled
public class MysqlWorkerJobRunningRepository extends AbstractJdbcWorkerJobRunningRepository {
    @Inject
    public MysqlWorkerJobRunningRepository(ApplicationContext applicationContext) {
        super(new MysqlRepository<>(WorkerJobRunning.class, applicationContext));
    }
}
