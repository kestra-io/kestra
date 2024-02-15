package io.kestra.repository.mysql;

import io.kestra.core.server.ServiceInstance;
import io.kestra.jdbc.repository.AbstractJdbcServiceInstanceRepository;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@MysqlRepositoryEnabled
public class MysqlServiceInstanceRepository extends AbstractJdbcServiceInstanceRepository {
    @Inject
    public MysqlServiceInstanceRepository(ApplicationContext applicationContext) {
        super(new MysqlRepository<>(ServiceInstance.class, applicationContext));
    }

}
