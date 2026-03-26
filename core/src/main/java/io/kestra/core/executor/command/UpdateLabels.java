package io.kestra.core.executor.command;

import java.time.Instant;
import java.util.List;

import io.kestra.core.events.EventId;
import io.kestra.core.models.Label;
import io.kestra.core.models.executions.Execution;

public record UpdateLabels(String tenantId,
    String namespace,
    String flowId,
    String executionId,
    Instant timestamp,
    EventId eventId,
    List<Label> labels) implements ExecutionCommand {
    public static UpdateLabels from(Execution execution, List<Label> labels) {
        return new UpdateLabels(
            execution.getTenantId(),
            execution.getNamespace(),
            execution.getFlowId(),
            execution.getId(),
            Instant.now(),
            EventId.create(),
            labels
        );
    }
}
