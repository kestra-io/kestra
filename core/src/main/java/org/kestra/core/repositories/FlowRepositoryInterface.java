package org.kestra.core.repositories;

import io.micronaut.data.model.Pageable;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.flows.Flow;

import java.util.List;
import java.util.Optional;

public interface FlowRepositoryInterface {
    Optional<Flow> findById(String namespace, String id, Optional<Integer> revision);

    default Flow findByExecution(Execution execution) {
        Optional<Flow> find = this.findById(
            execution.getNamespace(),
            execution.getFlowId(),
            Optional.of(execution.getFlowRevision())
        );

        if (find.isEmpty()) {
            throw new IllegalStateException("Unable to find flow '" + execution.getNamespace() + "." +
                execution.getFlowId() + "' with revision " + execution.getFlowRevision() + " on execution " +
                execution.getId()
            );
        } else {
            return find.get();
        }
    }

    default Optional<Flow> findById(String namespace, String id) {
        return this.findById(namespace, id, Optional.empty());
    }

    List<Flow> findRevisions(String namespace, String id);

    List<Flow> findAll();

    ArrayListTotal<Flow> find(String query, Pageable pageable);

    List<String> findDistinctNamespace(Optional<String> prefix);

    default Optional<Flow> exists(Flow flow) {
        return this.findRevisions(flow.getNamespace(), flow.getId())
            .stream()
            .filter(f -> f.equalsWithoutRevision(flow))
            .findFirst();
    }

    Flow save(Flow flow);

    void delete(Flow flow);
}
