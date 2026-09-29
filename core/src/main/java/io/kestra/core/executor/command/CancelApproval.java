package io.kestra.core.executor.command;

import java.time.Instant;

import io.kestra.core.events.EventId;
import io.kestra.core.models.executions.Execution;
import io.kestra.plugin.core.flow.Pause;

import jakarta.annotation.Nullable;

public record CancelApproval(String tenantId,
    String namespace,
    String flowId,
    String executionId,
    Instant timestamp,
    EventId eventId,
    String taskRunId,
    Pause.Resumed resumed,
    @Nullable String operationId) implements ExecutionCommand {
    public static CancelApproval from(Execution execution, String taskRunId, Pause.Resumed resumed) {
        return new CancelApproval(
            execution.getTenantId(),
            execution.getNamespace(),
            execution.getFlowId(),
            execution.getId(),
            Instant.now(),
            EventId.create(),
            taskRunId,
            resumed,
            null
        );
    }

    public CancelApproval withOperationId(String operationId) {
        return new CancelApproval(tenantId, namespace, flowId, executionId, timestamp, eventId, taskRunId, resumed, operationId);
    }
}
