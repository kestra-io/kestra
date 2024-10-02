package io.kestra.core.runners;

import io.kestra.core.models.HasUID;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.utils.IdUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import jakarta.validation.constraints.NotNull;

@Value
@AllArgsConstructor
@Builder
public class ExecutionQueued implements HasUID {
    String tenantId;

    @NotNull
    String namespace;

    @NotNull
    String flowId;

    @NotNull
    Execution execution;

    @NotNull
    Instant date;

    public static ExecutionQueued fromExecutionRunning(ExecutionRunning executionRunning) {
        return new ExecutionQueued(
            executionRunning.getTenantId(),
            executionRunning.getNamespace(),
            executionRunning.getFlowId(),
            executionRunning.getExecution(),
            Instant.now()
        );
    }

    /** {@inheritDoc **/
    @Override
    public String uid() {
        return IdUtils.fromParts(this.tenantId, this.namespace, this.flowId, this.execution.getId());
    }
}
