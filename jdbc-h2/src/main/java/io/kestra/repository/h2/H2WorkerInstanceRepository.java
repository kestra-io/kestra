package io.kestra.repository.h2;

import io.kestra.core.runners.WorkerInstance;
import io.kestra.jdbc.repository.AbstractJdbcWorkerInstanceRepository;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@H2RepositoryEnabled
public class H2WorkerInstanceRepository extends AbstractJdbcWorkerInstanceRepository {
    @Inject
    public H2WorkerInstanceRepository(ApplicationContext applicationContext) {
        super(new H2Repository<>(WorkerInstance.class, applicationContext));
    }
}
