package io.kestra.core.repositories;


import io.kestra.core.models.topologies.FlowTopology;

import java.util.List;

public interface FlowTopologyRepositoryInterface {
    List<FlowTopology> findByFlow(String namespace, String flowId, Boolean destinationOnly);

    FlowTopology save(FlowTopology flowTopology);
}
