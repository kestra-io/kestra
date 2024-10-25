package io.kestra.repository.mysql;

import io.kestra.core.server.ServiceInstance;
import io.kestra.jdbc.repository.AbstractJdbcServiceInstanceRepository;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.util.Optional;

@Singleton
@MysqlRepositoryEnabled
public class MysqlServiceInstanceRepository extends AbstractJdbcServiceInstanceRepository {
    @Inject
    public MysqlServiceInstanceRepository(@Named("serviceinstance") MysqlRepository<ServiceInstance> repository) {
        super(repository);
    }
}
