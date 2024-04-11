package io.kestra.repository.h2;

import io.kestra.core.models.topologies.FlowTopology;
import io.kestra.jdbc.repository.AbstractJdbcFlowTopologyRepository;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@H2RepositoryEnabled
public class H2FlowTopologyRepository extends AbstractJdbcFlowTopologyRepository {
    @Inject
    public H2FlowTopologyRepository(@Named("flowtopologies") H2Repository<FlowTopology> repository) {
        super(repository);
    }
}
