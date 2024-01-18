package io.kestra.core.runners;

import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;
import lombok.Builder;
import lombok.Data;

import javax.validation.constraints.NotNull;

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
