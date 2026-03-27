package io.kestra.core.repositories;

import java.util.List;

import io.kestra.core.models.topologies.FlowTopology;

public interface FlowTopologyRepositoryInterface {
    List<FlowTopology> findByFlow(String tenantId, String namespace, String flowId, Boolean destinationOnly);

    List<FlowTopology> findByNamespace(String tenantId, String namespace);

    List<FlowTopology> findAll(String tenantId);

    FlowTopology save(FlowTopology flowTopology);
}
