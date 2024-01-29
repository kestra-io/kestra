package io.kestra.core.runners;

import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SubflowExecutionResult {
    @NotNull
    private TaskRun parentTaskRun;

    @NotNull
    private String executionId;

    @NotNull
    private State.Type state;
}
