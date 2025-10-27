package io.kestra.core.runners;

import io.kestra.core.models.HasUID;
import io.kestra.core.models.executions.Execution;

import java.time.Instant;

public record ExecutionEvent(String tenantId, String namespace, String flowId, String executionId, Instant eventDate, ExecutionEventType eventType) implements HasUID {
    public ExecutionEvent(Execution execution, ExecutionEventType eventType) {
        this(execution.getTenantId(), execution.getNamespace(), execution.getFlowId(), execution.getId(), Instant.now(), eventType);
    }

    @Override
    public String uid() {
        return executionId;
    }
}
