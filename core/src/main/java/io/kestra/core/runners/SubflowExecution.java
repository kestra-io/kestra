package io.kestra.core.runners;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.tasks.ExecutableTask;
import io.kestra.core.models.tasks.Task;
import lombok.Builder;
import lombok.Data;

import jakarta.validation.constraints.NotNull;

@Data
@Builder
public class SubflowExecution<T extends Task & ExecutableTask<?>> {
    @NotNull
    private TaskRun parentTaskRun;

    @NotNull
    private T parentTask;

    @NotNull
    private Execution execution;
}
