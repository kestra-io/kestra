package io.kestra.repository.h2;

import io.kestra.core.server.ServiceInstance;
import io.kestra.jdbc.repository.AbstractJdbcServiceInstanceRepository;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@H2RepositoryEnabled
public class H2ServiceInstanceRepository extends AbstractJdbcServiceInstanceRepository {
    @Inject
    public H2ServiceInstanceRepository(ApplicationContext applicationContext) {
        super(new H2Repository<>(ServiceInstance.class, applicationContext));
    }
}
