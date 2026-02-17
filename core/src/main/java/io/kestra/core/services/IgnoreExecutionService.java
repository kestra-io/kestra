package io.kestra.core.services;

import com.google.common.annotations.VisibleForTesting;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.FlowId;
import jakarta.annotation.Nullable;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Service that deals with ignore execution from the command line.
 */
@Singleton
@Slf4j
public class IgnoreExecutionService {
    private volatile List<String> ignoredExecutions = Collections.emptyList();
    private volatile List<FlowId> ignoredFlows = Collections.emptyList();
    private volatile List<NamespaceId> ignoredNamespaces = Collections.emptyList();
    private volatile List<String> ignoredTenants = Collections.emptyList();
    private volatile List<String> ignoredIndexerRecords = Collections.emptyList();

    public synchronized void setIgnoredExecutions(List<String> ignoredExecutions) {
        this.ignoredExecutions = ignoredExecutions == null ? Collections.emptyList() : ignoredExecutions;
    }

    public synchronized void setIgnoredFlows(List<String> ignoredFlows) {
        this.ignoredFlows = ignoredFlows == null ? Collections.emptyList() : ignoredFlows.stream().map(s -> flowIdFrom(s)).filter(Objects::nonNull).toList();
    }

    public synchronized void setIgnoredNamespaces(List<String> ignoredNamespaces) {
        this.ignoredNamespaces = ignoredNamespaces == null ? Collections.emptyList() : ignoredNamespaces.stream().map(NamespaceId::from).filter(Objects::nonNull).toList();
    }

    public synchronized void setIgnoredTenants(List<String> ignoredTenants) {
        this.ignoredTenants = ignoredTenants == null ? Collections.emptyList() : ignoredTenants;
    }

    public synchronized void setIgnoredIndexerRecords(List<String> ignoredIndexerRecords) {
        this.ignoredIndexerRecords = ignoredIndexerRecords == null ? Collections.emptyList() : ignoredIndexerRecords;
    }

    /**
     * Warning: this method didn't check the flow, so it must be used only when neither of the others can be used.
     *
     * @return true if the execution references by this <code>executionId</code> should be ignored
     */
    public boolean ignoreExecution(String executionId) {
        return ignoredExecutions.contains(executionId);
    }

    /**
     * @return true if the execution should be ignored
     */
    public boolean ignoreExecution(Execution execution) {
        return ignoreExecution(execution.getTenantId(), execution.getNamespace(), execution.getFlowId(), execution.getId());
    }

    /**
     * @return true if the execution referenced by this task run should be ignored
     */
    public boolean ignoreExecution(TaskRun taskRun) {
        return ignoreExecution(taskRun.getTenantId(), taskRun.getNamespace(), taskRun.getFlowId(), taskRun.getExecutionId());
    }

    /**
     * Ignore an indexer record based on its key.
     * @param key the record key as computed by <code>QueueService.key(record)</code>, can be null
     */
    public boolean ignoreIndexerRecord(@Nullable String key) {
        return key != null && ignoredIndexerRecords.contains(key);
    }

    @VisibleForTesting
    boolean ignoreExecution(String tenant, String namespace, String flow, String executionId) {
        return (tenant != null && ignoredTenants.contains(tenant)) ||
            ignoredNamespaces.contains(new NamespaceId(tenant, namespace)) ||
            ignoredFlows.contains(FlowId.of(tenant, namespace, flow, null)) ||
            (executionId != null && ignoredExecutions.contains(executionId));
    }

    private static String[] splitIdParts(String id) {
        return id.split("\\|");
    }

    private FlowId flowIdFrom(String flowId) {
        String[] parts = IgnoreExecutionService.splitIdParts(flowId);
        if (parts.length == 3) {
            return FlowId.of(parts[0], parts[1], parts[2], null);
        } else {
            log.error("Invalid flow skip with values: '{}'", flowId);
        }

        return null;
    }

    record NamespaceId(String tenant, String namespace) {
        static @Nullable NamespaceId from(String namespaceId) {
            String[] parts = IgnoreExecutionService.splitIdParts(namespaceId);

            if (parts.length == 2) {
                return new NamespaceId(parts[0], parts[1]);
            } else {
                log.error("Invalid namespace skip with values:'{}'", namespaceId);
            }

            return null;
        }
    };
}
