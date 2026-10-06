package io.kestra.core.executor.command;

import java.time.Instant;
import java.util.Optional;

import io.kestra.core.events.Actor;
import io.kestra.core.events.EventId;
import io.kestra.core.models.executions.Execution;

import jakarta.annotation.Nullable;
import lombok.With;

public record ResumeFromBreakpoint(String tenantId,
    String namespace,
    String flowId,
    String executionId,
    Instant timestamp,
    EventId eventId,
    Optional<String> breakpoints,
    @Nullable String operationId,
    @With @Nullable Actor actor) implements ExecutionCommand {
    public static ResumeFromBreakpoint from(Execution execution, Optional<String> breakpoints) {
        return new ResumeFromBreakpoint(
            execution.getTenantId(),
            execution.getNamespace(),
            execution.getFlowId(),
            execution.getId(),
            Instant.now(),
            EventId.create(),
            breakpoints,
            null,
            null
        );
    }

    public ResumeFromBreakpoint withOperationId(String operationId) {
        return new ResumeFromBreakpoint(tenantId, namespace, flowId, executionId, timestamp, eventId, breakpoints, operationId, actor);
    }
}
