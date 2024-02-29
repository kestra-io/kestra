package io.kestra.core.models.executions;

import lombok.AllArgsConstructor;
import lombok.Value;
import io.kestra.core.models.tasks.Task;

import jakarta.validation.constraints.NotNull;

@Value
@AllArgsConstructor
public class NextTaskRun {
    @NotNull
    TaskRun taskRun;

    @NotNull
    Task task;
}
