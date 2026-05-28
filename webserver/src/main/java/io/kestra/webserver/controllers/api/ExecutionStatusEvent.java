package io.kestra.webserver.controllers.api;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;

import jakarta.validation.constraints.NotNull;

public record ExecutionStatusEvent(@NotNull String executionId, @NotNull String tenantId, @NotNull String namespace, @NotNull String flowId, @NotNull State state) {
    public static ExecutionStatusEvent of(Execution execution) {
        return new ExecutionStatusEvent(execution.getId(), execution.getTenantId(), execution.getNamespace(), execution.getFlowId(), execution.getState());
    }
}
