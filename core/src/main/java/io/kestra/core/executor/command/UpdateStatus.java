package io.kestra.core.executor.command;

import io.kestra.core.events.EventId;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;

import java.time.Instant;

public record UpdateStatus(String tenantId,
                           String namespace,
                           String flowId,
                           String executionId,
                           Instant timestamp,
                           EventId eventId,
                           State.Type state) implements  ExecutionCommand {
    public static UpdateStatus from(Execution execution, State.Type state) {
        return new UpdateStatus(
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
