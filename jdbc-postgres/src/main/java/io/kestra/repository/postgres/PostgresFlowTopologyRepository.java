package io.kestra.repository.postgres;

import io.kestra.core.models.topologies.FlowTopology;
import io.kestra.jdbc.repository.AbstractJdbcFlowTopologyRepository;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@PostgresRepositoryEnabled
public class PostgresFlowTopologyRepository extends AbstractJdbcFlowTopologyRepository {
    @Inject
    public PostgresFlowTopologyRepository(ApplicationContext applicationContext) {
        super(new PostgresRepository<>(FlowTopology.class, applicationContext));
    }
}
