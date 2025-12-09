package io.kestra.core.executor.command;

import io.kestra.core.events.EventId;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.micronaut.core.annotation.Nullable;

import java.time.Instant;

public record Unqueue(String tenantId,
                      String namespace,
                      String flowId,
                      String executionId,
                      Instant timestamp,
                      EventId eventId,
                      @Nullable State.Type state) implements ExecutionCommand {
    public static Unqueue from(Execution execution, @Nullable State.Type state) {
        return new Unqueue(
            execution.getTenantId(),
            execution.getNamespace(),
            execution.getFlowId(),
            execution.getId(),
            Instant.now(),
            EventId.create(),
            state
        );
    }
}
