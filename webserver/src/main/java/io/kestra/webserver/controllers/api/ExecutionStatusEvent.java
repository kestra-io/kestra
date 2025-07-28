package io.kestra.webserver.controllers.api;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;

public record ExecutionStatusEvent(String executionId, String tenantId, String namespace, String flowId, State state) {
    public static ExecutionStatusEvent of(Execution execution) {
        return new ExecutionStatusEvent(execution.getId(), execution.getTenantId(), execution.getNamespace(), execution.getFlowId(), execution.getState());
    }
}
