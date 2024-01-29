package io.kestra.core.runners;

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
public class ExecutionQueued {
    String tenantId;

    @NotNull
    String namespace;

    @NotNull
    String flowId;

    @NotNull
    Execution execution;

    @NotNull
    Instant date;

    public String uid() {
        return IdUtils.fromParts(this.tenantId, this.namespace, this.flowId, this.execution.getId());
    }
}
