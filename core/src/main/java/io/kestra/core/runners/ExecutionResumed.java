package io.kestra.core.runners;

import io.kestra.core.models.flows.State;
import io.kestra.core.utils.IdUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

import javax.validation.constraints.NotNull;

@Value
@AllArgsConstructor
@Builder
public class ExecutionResumed {
    @NotNull
    String taskRunId;

    @NotNull
    String executionId;

    @NotNull State.Type state;

    public String uid() {
        return IdUtils.fromParts(this.executionId, this.taskRunId);
    }
}
