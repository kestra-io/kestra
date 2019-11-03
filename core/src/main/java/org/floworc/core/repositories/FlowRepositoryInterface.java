package org.floworc.core.repositories;

import org.floworc.core.models.executions.Execution;
import org.floworc.core.models.flows.Flow;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface FlowRepositoryInterface {
    Optional<Flow> findById(String namespace, String id, Optional<Integer> revision);

    default Optional<Flow> findByExecution(Execution execution) {
        return this.findById(execution.getNamespace(), execution.getFlowId(), Optional.of(execution.getFlowRevision()));
    }

    default Optional<Flow> findById(String namespace, String id) {
        return this.findById(namespace, id, Optional.empty());
    }

    List<Flow> findRevisions(String namespace, String id);

    List<Flow> findAll();

    Flow save(Flow flow);

    void delete(Flow flow);
}
