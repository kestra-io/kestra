package io.kestra.core.executor.command;

import io.kestra.core.events.EventId;
import io.kestra.core.models.executions.Execution;
import jakarta.annotation.Nullable;

import java.time.Instant;
import java.util.Optional;

public record Replay(String tenantId,
                     String namespace,
                     String flowId,
                     String executionId,
                     Instant timestamp,
                     EventId eventId,
                     @Nullable String taskRunId,
                     @Nullable Integer revision,
                     Optional<String> breakpoints) implements ExecutionCommand {
    public static Replay from(Execution execution, @Nullable String taskRunId, @Nullable Integer revision, Optional<String> breakpoints) {
        return new Replay(
            execution.getTenantId(),
            execution.getNamespace(),
            execution.getFlowId(),
            execution.getId(),
            Instant.now(),
            EventId.create(),
            taskRunId,
            revision,
            breakpoints
        );
    }
}
