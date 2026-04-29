package io.kestra.core.executor.command;

import java.time.Instant;

import io.kestra.core.events.EventId;
import io.kestra.core.models.executions.Execution;

import jakarta.annotation.Nullable;

public record Restart(String tenantId,
    String namespace,
    String flowId,
    String executionId,
    Instant timestamp,
    EventId eventId,
    @Nullable Integer revision,
    @Nullable String operationId) implements ExecutionCommand {
    public static Restart from(Execution execution, Integer revision) {
        return new Restart(
            execution.getTenantId(),
            execution.getNamespace(),
            execution.getFlowId(),
            execution.getId(),
            Instant.now(),
            EventId.create(),
            revision,
            null
        );
    }

    public Restart withOperationId(String operationId) {
        return new Restart(tenantId, namespace, flowId, executionId, timestamp, eventId, revision, operationId);
    }
}
