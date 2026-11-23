package io.kestra.core.executor.command;

import java.time.Instant;

import io.kestra.core.events.Actor;
import io.kestra.core.events.EventId;
import io.kestra.core.models.executions.Execution;

import jakarta.annotation.Nullable;

public record ForceRun(String tenantId,
    String namespace,
    String flowId,
    String executionId,
    Instant timestamp,
    EventId eventId,
    @Nullable String operationId,
    @Nullable Actor actor) implements ExecutionCommand {

    public static ForceRun from(Execution execution) {
        return new ForceRun(
            execution.getTenantId(),
            execution.getNamespace(),
            execution.getFlowId(),
            execution.getId(),
            Instant.now(),
            EventId.create(),
            null,
            null
        );
    }

    public ForceRun withOperationId(String operationId) {
        return new ForceRun(tenantId, namespace, flowId, executionId, timestamp, eventId, operationId, actor);
    }

    public ForceRun withActor(Actor actor) {
        return new ForceRun(tenantId, namespace, flowId, executionId, timestamp, eventId, operationId, actor);
    }
}
