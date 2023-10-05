package io.kestra.core.models.executions;

import lombok.AllArgsConstructor;
import lombok.Value;
import io.kestra.core.models.tasks.Task;
import lombok.With;

import javax.validation.constraints.NotNull;

@Value
@AllArgsConstructor
public class NextTaskRun {
    @NotNull
    @With
    TaskRun taskRun;

    @NotNull
    Task task;
}
