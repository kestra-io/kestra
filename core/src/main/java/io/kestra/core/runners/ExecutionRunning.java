package io.kestra.core.runners;

import io.kestra.core.models.HasUID;
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
public class ExecutionRunning implements HasUID {
    String tenantId;

    @NotNull
    String namespace;

    @NotNull
    String flowId;

    @With
    Execution execution;

    @With
    ConcurrencyState concurrencyState;

    @Override
    public String uid() {
        return IdUtils.fromPartsAndSeparator('|', this.tenantId, this.namespace, this.flowId, this.execution.getId());
    }

    // Note: the KILLED state is only used in the Kafka implementation to difference between purging terminated running execution (null)
    // and purging killed execution which need special treatment
    public enum ConcurrencyState { CREATED, RUNNING, QUEUED, CANCELLED, FAILED, KILLED }
}
