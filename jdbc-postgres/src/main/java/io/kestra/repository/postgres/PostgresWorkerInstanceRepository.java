package io.kestra.repository.postgres;

import io.kestra.core.runners.WorkerInstance;
import io.kestra.jdbc.repository.AbstractJdbcWorkerInstanceRepository;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@PostgresRepositoryEnabled
public class PostgresWorkerInstanceRepository extends AbstractJdbcWorkerInstanceRepository {
    @Inject
    public PostgresWorkerInstanceRepository(ApplicationContext applicationContext) {
        super(new PostgresRepository<>(WorkerInstance.class, applicationContext));
    }
}
