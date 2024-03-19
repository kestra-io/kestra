package io.kestra.repository.mysql;

import io.kestra.core.models.topologies.FlowTopology;
import io.kestra.jdbc.repository.AbstractJdbcFlowTopologyRepository;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@MysqlRepositoryEnabled
public class MysqlFlowTopologyRepository extends AbstractJdbcFlowTopologyRepository {
    @Inject
    public MysqlFlowTopologyRepository(@Named("flowtopologies") MysqlRepository<FlowTopology> repository) {
        super(repository);
    }
}
