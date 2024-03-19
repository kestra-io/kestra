package io.kestra.repository.postgres;

import io.kestra.core.models.topologies.FlowTopology;
import io.kestra.jdbc.repository.AbstractJdbcFlowTopologyRepository;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@PostgresRepositoryEnabled
public class PostgresFlowTopologyRepository extends AbstractJdbcFlowTopologyRepository {
    @Inject
    public PostgresFlowTopologyRepository(@Named("flowtopologies") PostgresRepository<FlowTopology> repository) {
        super(repository);
    }
}
