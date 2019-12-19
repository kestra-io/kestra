package org.floworc.core.repositories;

import io.micronaut.data.model.Pageable;
import org.floworc.core.models.executions.Execution;
import org.floworc.core.models.flows.Flow;

import java.util.List;
import java.util.Optional;

public interface FlowRepositoryInterface {
    Optional<Flow> findById(String namespace, String id, Optional<Integer> revision);

    default Optional<Flow> findByExecution(Execution execution) {
        return this.findById(execution.getNamespace(), execution.getFlowId(), Optional.of(execution.getFlowRevision()));
    }

    default Optional<Flow> findById(String namespace, String id) {
        return this.findById(namespace, id, Optional.empty());
    }

    default Optional<Flow> exists(Flow flow) {
        return this.findRevisions(flow.getNamespace(), flow.getId())
            .stream()
            .filter(f -> f.equalsWithoutRevision(flow))
            .findFirst();
    }

    List<Flow> findRevisions(String namespace, String id);

    List<Flow> findAll();

    ArrayListTotal<Flow> findByNamespace(String namespace, Pageable pageable);

    ArrayListTotal<Flow> find(String query, Pageable pageable);

    Flow save(Flow flow);

    void delete(Flow flow);

    ArrayListTotal<String> findNamespaces(Optional<String> prefix);
}
