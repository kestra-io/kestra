package io.kestra.core.runners;

import java.util.Collection;
import java.util.Optional;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.flows.FlowWithSource;

public interface FlowMetaStoreInterface {

    /**
     * Checks whether a given namespace exists.
     * <p>
     * A namespace is considered existing if at least one Flow is within the namespace or a parent namespace.
     *
     * @param tenant The tenant ID
     * @param namespace The namespace - cannot be null.
     * @return {@code true} if the namespace exist. Otherwise {@link false}.
     */
    boolean isNamespaceExists(String tenant, String namespace);

    /**
     * Find all flows.
     * WARNING: this method will NOT check if the namespace is allowed, so it should not be used inside a task.
     */
    Collection<FlowWithSource> allLastVersion();

    /**
     * Find a flow.
     * WARNING: this method will NOT check if the namespace is allowed, so it should not be used inside a task.
     */
    Optional<FlowInterface> findById(String tenantId, String namespace, String id, Optional<Integer> revision);

    /**
     * Find a flow.
     * This method will check if the namespace is allowed, so it can be used inside a task.
     */
    default Optional<FlowInterface> findByIdFromTask(String tenantId, String namespace, String id, Optional<Integer> revision, String fromTenant, String fromNamespace, String fromId) {
        return this.findById(tenantId, namespace, id, revision);
    }

    /**
     * Find a flow from an execution.
     * WARNING: this method will NOT check if the namespace is allowed, so it should not be used inside a task.
     */
    default Optional<FlowInterface> findByExecution(Execution execution) {
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

    Optional<FlowWithSource> findByExecutionThenInjectDefaults(Execution execution);
}
