package io.kestra.repository.mysql;

import java.util.List;

import org.jooq.Condition;

import io.kestra.core.repositories.RepositoryBean;
import io.kestra.core.server.ServiceInstance;
import io.kestra.jdbc.repository.AbstractJdbcServiceInstanceRepository;

import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@MysqlRepositoryEnabled
public class MysqlServiceInstanceRepository extends AbstractJdbcServiceInstanceRepository {
    @Inject
    public MysqlServiceInstanceRepository(@Named("serviceinstance") MysqlRepository<ServiceInstance> repository) {
        super(repository);
    }

    @Override
    protected Condition findQueryCondition(String query) {
        return jdbcRepository.fullTextCondition(List.of("service_id", "service_type", "server_hostname", "server_version"), query);
    }
}
