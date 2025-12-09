package io.kestra.core.executor.command;

import io.kestra.core.events.EventId;
import io.kestra.core.models.executions.Execution;
import jakarta.annotation.Nullable;

import java.time.Instant;

public record Restart(String tenantId,
                      String namespace,
                      String flowId,
                      String executionId,
                      Instant timestamp,
                      EventId eventId,
                      @Nullable Integer revision) implements ExecutionCommand {
    public static Restart from(Execution execution, Integer revision) {
        return new Restart(
            execution.getTenantId(),
            execution.getNamespace(),
            execution.getFlowId(),
            execution.getId(),
            Instant.now(),
            EventId.create(),
            revision
        );
    }
}
