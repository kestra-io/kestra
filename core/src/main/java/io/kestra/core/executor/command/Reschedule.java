package io.kestra.core.executor.command;

import java.time.Instant;

import io.kestra.core.events.Actor;
import io.kestra.core.events.EventId;
import io.kestra.core.models.executions.Execution;

import jakarta.annotation.Nullable;

public record Reschedule(String tenantId,
    String namespace,
    String flowId,
    String executionId,
    Instant timestamp,
    EventId eventId,
    @Nullable String operationId,
    @Nullable Actor actor,
    Instant scheduleDate) implements ExecutionCommand {

    public static Reschedule from(Execution execution, Instant scheduleDate) {
        return new Reschedule(
            execution.getTenantId(),
            execution.getNamespace(),
            execution.getFlowId(),
            execution.getId(),
            Instant.now(),
            EventId.create(),
            null,
            null,
            scheduleDate
        );
    }

    public Reschedule withOperationId(String operationId) {
        return new Reschedule(tenantId, namespace, flowId, executionId, timestamp, eventId, operationId, actor, scheduleDate);
    }

    public Reschedule withActor(Actor actor) {
        return new Reschedule(tenantId, namespace, flowId, executionId, timestamp, eventId, operationId, actor, scheduleDate);
    }
}
