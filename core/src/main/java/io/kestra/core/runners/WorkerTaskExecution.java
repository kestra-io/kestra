package io.kestra.core.runners;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.tasks.ExecutableTask;
import io.kestra.core.models.tasks.Task;
import lombok.Builder;
import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
@Builder
public class WorkerTaskExecution<T extends Task & ExecutableTask<?>> {
    @NotNull
    private TaskRun taskRun;

    @NotNull
    private T task;

    @NotNull
    private Execution execution;
}
