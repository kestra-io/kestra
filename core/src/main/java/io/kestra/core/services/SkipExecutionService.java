package io.kestra.core.services;

import com.google.common.annotations.VisibleForTesting;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import jakarta.annotation.Nullable;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Singleton
@Slf4j
public class SkipExecutionService {
    private volatile List<String> skipExecutions = Collections.emptyList();
    private volatile List<FlowId> skipFlows = Collections.emptyList();
    private volatile List<NamespaceId> skipNamespaces = Collections.emptyList();
    private volatile List<String> skipTenants = Collections.emptyList();
    private volatile List<String> skipIndexerRecords = Collections.emptyList();

    public synchronized void setSkipExecutions(List<String> skipExecutions) {
        this.skipExecutions = skipExecutions == null ? Collections.emptyList() : skipExecutions;
    }

    public synchronized void setSkipFlows(List<String> skipFlows) {
        this.skipFlows = skipFlows == null ? Collections.emptyList() : skipFlows.stream().map(FlowId::from).filter(Objects::nonNull).toList();
    }

    public synchronized void setSkipNamespaces(List<String> skipNamespaces) {
        this.skipNamespaces = skipNamespaces == null ? Collections.emptyList() : skipNamespaces.stream().map(NamespaceId::from).filter(Objects::nonNull).toList();
    }

    public synchronized void setSkipTenants(List<String> skipTenants) {
        this.skipTenants = skipTenants == null ? Collections.emptyList() : skipTenants;
    }

    public synchronized void setSkipIndexerRecords(List<String> skipIndexerRecords) {
        this.skipIndexerRecords = skipIndexerRecords == null ? Collections.emptyList() : skipIndexerRecords;
    }

    /**
     * Warning: this method didn't check the flow, so it must be used only when neither of the others can be used.
     */
    public boolean skipExecution(String executionId) {
        return skipExecutions.contains(executionId);
    }

    public boolean skipExecution(Execution execution) {
        return skipExecution(execution.getTenantId(), execution.getNamespace(), execution.getFlowId(), execution.getId());
    }

    public boolean skipExecution(TaskRun taskRun) {
        return skipExecution(taskRun.getTenantId(), taskRun.getNamespace(), taskRun.getFlowId(), taskRun.getExecutionId());
    }

    /**
     * Skip an indexer records based on its key.
     * @param key the record key as computed by <code>QueueService.key(record)</code>, can be null
     */
    public boolean skipIndexerRecord(@Nullable String key) {
        return key != null && skipIndexerRecords.contains(key);
    }

    @VisibleForTesting
    boolean skipExecution(String tenant, String namespace, String flow, String executionId) {
        return (tenant != null && skipTenants.contains(tenant)) ||
            skipNamespaces.contains(new NamespaceId(tenant, namespace)) ||
            skipFlows.contains(new FlowId(tenant, namespace, flow)) ||
            (executionId != null && skipExecutions.contains(executionId));
    }

    private static String[] splitIdParts(String id) {
        return id.split("\\|");
    }

    record FlowId(String tenant, String namespace, String flow) {
        static @Nullable FlowId from(String flowId) {
            String[] parts = SkipExecutionService.splitIdParts(flowId);
            if (parts.length == 3) {
                return new FlowId(parts[0], parts[1], parts[2]);
            } else if (parts.length == 2) {
                return new FlowId(null, parts[0], parts[1]);
            } else {
                log.error("Invalid flow skip with values: '{}'", flowId);
            }

            return null;
        }
    };

    record NamespaceId(String tenant, String namespace) {
        static @Nullable NamespaceId from(String namespaceId) {
            String[] parts = SkipExecutionService.splitIdParts(namespaceId);

            if (parts.length == 2) {
                return new NamespaceId(parts[0], parts[1]);
            } else {
                log.error("Invalid namespace skip with values:'{}'", namespaceId);
            }

            return null;
        }
    };
}
