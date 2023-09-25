package io.kestra.repository.mysql;

import io.kestra.core.runners.WorkerInstance;
import io.kestra.jdbc.repository.AbstractJdbcWorkerInstanceRepository;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@MysqlRepositoryEnabled
public class MysqlWorkerInstanceRepository extends AbstractJdbcWorkerInstanceRepository {
    @Inject
    public MysqlWorkerInstanceRepository(ApplicationContext applicationContext) {
        super(new MysqlRepository<>(WorkerInstance.class, applicationContext));
    }
}
