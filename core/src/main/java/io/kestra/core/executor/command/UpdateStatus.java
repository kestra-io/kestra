package io.kestra.core.executor.command;

import java.time.Instant;

import io.kestra.core.events.EventId;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;

import jakarta.annotation.Nullable;

public record UpdateStatus(String tenantId,
    String namespace,
    String flowId,
    String executionId,
    Instant timestamp,
    EventId eventId,
    State.Type state,
    @Nullable String operationId) implements ExecutionCommand {
    public static UpdateStatus from(Execution execution, State.Type state) {
        return new UpdateStatus(
            execution.getTenantId(),
            execution.getNamespace(),
            execution.getFlowId(),
            execution.getId(),
            Instant.now(),
            EventId.create(),
            state,
            null
        );
    }

    public UpdateStatus withOperationId(String operationId) {
        return new UpdateStatus(tenantId, namespace, flowId, executionId, timestamp, eventId, state, operationId);
    }
}
