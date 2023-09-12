package io.kestra.repository.memory;

import io.kestra.core.models.topologies.FlowTopology;
import io.kestra.core.repositories.FlowTopologyRepositoryInterface;
import jakarta.inject.Singleton;

import java.util.List;

@Singleton
@MemoryRepositoryEnabled
public class MemoryFlowTopologyRepository implements FlowTopologyRepositoryInterface {

    @Override
    public List<FlowTopology> findByFlow(String tenantId, String namespace, String flowId, Boolean destinationOnly) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<FlowTopology> findByNamespace(String tenantId, String namespace) {
        throw new UnsupportedOperationException();
    }

    @Override
    public FlowTopology save(FlowTopology flowTopology) {
        throw new UnsupportedOperationException();
    }
}
