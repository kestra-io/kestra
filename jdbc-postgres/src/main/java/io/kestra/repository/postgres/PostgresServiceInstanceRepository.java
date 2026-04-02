package io.kestra.repository.postgres;

import io.kestra.core.repositories.RepositoryBean;
import io.kestra.core.server.ServiceInstance;
import io.kestra.jdbc.repository.AbstractJdbcServiceInstanceRepository;

import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@PostgresRepositoryEnabled
public class PostgresServiceInstanceRepository extends AbstractJdbcServiceInstanceRepository {
    @Inject
    public PostgresServiceInstanceRepository(@Named("serviceinstance") PostgresRepository<ServiceInstance> repository) {
        super(repository);
    }
}
