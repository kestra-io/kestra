package io.kestra.core.runners;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.utils.IdUtils;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import lombok.With;

@Value
@AllArgsConstructor
@Builder
public class ExecutionRunning {
    String tenantId;

    @NotNull
    String namespace;

    @NotNull
    String flowId;

    @With
    Execution execution;

    @With
    ConcurrencyState concurrencyState;

    public String uid() {
        return IdUtils.fromPartsAndSeparator('|', this.tenantId, this.namespace, this.flowId, this.execution.getId());
    }

    public enum ConcurrencyState { CREATED, RUNNING, QUEUED }
}
