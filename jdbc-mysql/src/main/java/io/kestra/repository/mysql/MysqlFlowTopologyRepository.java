package io.kestra.repository.mysql;

import io.kestra.core.models.topologies.FlowTopology;
import io.kestra.jdbc.repository.AbstractJdbcFlowTopologyRepository;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@MysqlRepositoryEnabled
public class MysqlFlowTopologyRepository extends AbstractJdbcFlowTopologyRepository {
    @Inject
    public MysqlFlowTopologyRepository(ApplicationContext applicationContext) {
        super(new MysqlRepository<>(FlowTopology.class, applicationContext));
    }
}
