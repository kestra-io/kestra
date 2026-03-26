package io.kestra.core.runners;

import java.time.Instant;

import io.kestra.core.models.HasUID;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.queues.event.BroadcastEvent;

public record FollowExecutionEvent(String tenantId, String namespace, String flowId, String executionId, Instant eventDate, ExecutionEventType eventType) implements HasUID, BroadcastEvent {
    public FollowExecutionEvent(Execution execution, ExecutionEventType eventType) {
        this(execution.getTenantId(), execution.getNamespace(), execution.getFlowId(), execution.getId(), Instant.now(), eventType);
    }

    @Override
    public String uid() {
        return executionId;
    }

    @Override
    public String key() {
        return executionId;
    }
}
