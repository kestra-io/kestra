package io.kestra.core.repositories;


import io.kestra.core.models.topologies.FlowTopology;

import java.util.List;

public interface FlowTopologyRepositoryInterface {
    List<FlowTopology> findByFlow(String tenantId, String namespace, String flowId, Boolean destinationOnly);

    List<FlowTopology> findByNamespace(String tenantId, String namespace);

    FlowTopology save(FlowTopology flowTopology);
}
