package io.kestra.core.models.executions;

import io.kestra.core.models.flows.FlowId;
import jakarta.annotation.Nullable;

public record ExecutionId(String tenantId, String namespace, String flowId, String executionId, @Nullable Integer flowRevision) {
    public ExecutionId (FlowId flowId, String executionId){
        this(flowId.getTenantId(), flowId.getNamespace(), flowId.getId(), executionId, flowId.getRevision());
    }
}
