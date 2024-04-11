package io.kestra.repository.h2;

import io.kestra.core.server.ServiceInstance;
import io.kestra.jdbc.repository.AbstractJdbcServiceInstanceRepository;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@H2RepositoryEnabled
public class H2ServiceInstanceRepository extends AbstractJdbcServiceInstanceRepository {
    @Inject
    public H2ServiceInstanceRepository(@Named("serviceinstance") H2Repository<ServiceInstance> repository) {
        super(repository);
    }
}
