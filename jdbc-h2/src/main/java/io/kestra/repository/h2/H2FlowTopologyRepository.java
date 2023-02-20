package io.kestra.repository.h2;

import io.kestra.core.models.topologies.FlowTopology;
import io.kestra.jdbc.repository.AbstractJdbcFlowTopologyRepository;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@H2RepositoryEnabled
public class H2FlowTopologyRepository extends AbstractJdbcFlowTopologyRepository {
    @Inject
    public H2FlowTopologyRepository(ApplicationContext applicationContext) {
        super(new H2Repository<>(FlowTopology.class, applicationContext));
    }
}
