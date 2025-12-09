package io.kestra.core.executor.command;

import io.kestra.core.events.EventId;
import io.kestra.core.models.executions.Execution;

import java.time.Instant;

public record ForceRun(String tenantId,
                       String namespace,
                       String flowId,
                       String executionId,
                       Instant timestamp,
                       EventId eventId) implements ExecutionCommand {

    public static ForceRun from(Execution execution) {
        return new ForceRun(
            execution.getTenantId(),
            execution.getNamespace(),
            execution.getFlowId(),
            execution.getId(),
            Instant.now(),
            EventId.create()
        );
    }
}
