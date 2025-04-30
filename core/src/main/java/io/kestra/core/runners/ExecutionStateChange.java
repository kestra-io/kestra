package io.kestra.core.runners;

import io.kestra.core.models.HasUID;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

@Value
@AllArgsConstructor
@Builder
public class ExecutionStateChange implements HasUID {
    @NotNull
    String tenantId;

    @NotNull
    String namespace;

    @NotNull
    String flowId;

    @NotNull
    String executionId;

    @NotNull
    State.Type oldState;

    @NotNull
    State.Type newState;

    @Override
    public String uid() {
        return executionId;
    }

    public static ExecutionStateChange fromExecution(Execution execution, State.Type oldState, State.Type newState) {
        return new ExecutionStateChange(execution.getTenantId(), execution.getNamespace(), execution.getFlowId(), execution.getId(), oldState, newState);
    }
}
