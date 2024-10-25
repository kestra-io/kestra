package io.kestra.core.runners;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowWithSource;

import java.util.Collection;
import java.util.Optional;

public interface FlowExecutorInterface {
    /**
     * Find all flows.
     * WARNING: this method will NOT check if the namespace is allowed, so it should not be used inside a task.
     */
    Collection<FlowWithSource> allLastVersion();

    /**
     * Find a flow.
     * WARNING: this method will NOT check if the namespace is allowed, so it should not be used inside a task.
     */
    Optional<FlowWithSource> findById(String tenantId, String namespace, String id, Optional<Integer> revision);

    /**
     * Whether the FlowExecutorInterface is ready to be used.
     */
    Boolean isReady();

    /**
     * Find a flow.
     * This method will check if the namespace is allowed, so it can be used inside a task.
     */
    default Optional<FlowWithSource> findByIdFromTask(String tenantId, String namespace, String id, Optional<Integer> revision, String fromTenant, String fromNamespace, String fromId) {
        return this.findById(
            tenantId,
            namespace,
            id,
            revision
        );
    }

    /**
     * Find a flow from an execution.
     * WARNING: this method will NOT check if the namespace is allowed, so it should not be used inside a task.
     */
    default Optional<FlowWithSource> findByExecution(Execution execution) {
        if (execution.getFlowRevision() == null) {
            return Optional.empty();
        }

        return this.findById(
            execution.getTenantId(),
            execution.getNamespace(),
            execution.getFlowId(),
            Optional.of(execution.getFlowRevision())
        );
    }
}
