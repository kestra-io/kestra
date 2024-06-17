package io.kestra.core.services;

import com.google.common.annotations.VisibleForTesting;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import jakarta.inject.Singleton;

import java.util.Collections;
import java.util.List;

@Singleton
public class SkipExecutionService {
    private volatile List<String> skipExecutions = Collections.emptyList();
    private volatile List<FlowId> skipFlows = Collections.emptyList();
    private volatile List<String> skipNamespaces = Collections.emptyList();

    public synchronized void setSkipExecutions(List<String> skipExecutions) {
        this.skipExecutions = skipExecutions;
    }

    public synchronized void setSkipFlows(List<String> skipFlows) {
        this.skipFlows = skipFlows == null ? Collections.emptyList() : skipFlows.stream().map(flow -> FlowId.from(flow)).toList();
    }

    public synchronized void setSkipNamespaces(List<String> skipNamespaces) {
        this.skipNamespaces = skipNamespaces == null ? Collections.emptyList() : skipNamespaces;
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

    @VisibleForTesting
    boolean skipExecution(String tenant, String namespace, String flow, String executionId) {
        return skipNamespaces.contains(namespace) ||
            skipFlows.contains(new FlowId(tenant, namespace, flow)) ||
            skipExecutions.contains(executionId);
    }

    record FlowId(String tenant, String namespace, String flow) {
        static FlowId from(String flowId) {
            String[] parts = flowId.split("\\|");
            if (parts.length == 3) {
                return new FlowId(parts[0], parts[1], parts[2]);
            }
            return new FlowId(null, parts[0], parts[1]);
        }
    };
}
