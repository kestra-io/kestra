package io.kestra.repository.postgres;

import io.kestra.core.server.ServiceInstance;
import io.kestra.jdbc.repository.AbstractJdbcServiceInstanceRepository;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@PostgresRepositoryEnabled
public class PostgresServiceInstanceRepository extends AbstractJdbcServiceInstanceRepository {
    @Inject
    public PostgresServiceInstanceRepository(ApplicationContext applicationContext) {
        super(new PostgresRepository<>(ServiceInstance.class, applicationContext));
    }
}
