package io.kestra.core.runners;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;

import java.util.Collection;
import java.util.Optional;

public interface FlowExecutorInterface {
    Collection<Flow> allLastVersion();

    Optional<Flow> findById(String namespace, String id, Optional<Integer> revision);

    Boolean isReady();

    default Optional<Flow> findByIdFromFlowTask(String namespace, String id, Optional<Integer> revision, String fromNamespace, String fromId) {
        return this.findById(
            namespace,
            id,
            revision
        );
    }

    default Optional<Flow> findByExecution(Execution execution) {
        return this.findById(
            execution.getNamespace(),
            execution.getFlowId(),
            Optional.of(execution.getFlowRevision())
        );
    }
}
