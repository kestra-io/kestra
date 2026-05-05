package io.kestra.core.executor.command;

import java.time.Instant;
import java.util.Map;

import io.kestra.core.events.EventId;
import io.kestra.core.models.executions.Execution;
import io.kestra.plugin.core.flow.Pause;

import jakarta.annotation.Nullable;

public record Resume(String tenantId,
    String namespace,
    String flowId,
    String executionId,
    Instant timestamp,
    EventId eventId,
    Pause.Resumed resumed,
    @Nullable Map<String, Object> resumeInputs,
    @Nullable String operationId) implements ExecutionCommand {
    public static Resume from(Execution execution, Pause.Resumed resumed) {
        return new Resume(
            execution.getTenantId(),
            execution.getNamespace(),
            execution.getFlowId(),
            execution.getId(),
            Instant.now(),
            EventId.create(),
            resumed,
            null,
            null
        );
    }

    public static Resume from(Execution execution, Pause.Resumed resumed, @Nullable Map<String, Object> resumeInputs) {
        return new Resume(
            execution.getTenantId(),
            execution.getNamespace(),
            execution.getFlowId(),
            execution.getId(),
            Instant.now(),
            EventId.create(),
            resumed,
            resumeInputs,
            null
        );
    }

    public Resume withOperationId(String operationId) {
        return new Resume(tenantId, namespace, flowId, executionId, timestamp, eventId, resumed, resumeInputs, operationId);
    }
}
