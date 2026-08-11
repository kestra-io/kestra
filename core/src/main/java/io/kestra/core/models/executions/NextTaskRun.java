package io.kestra.core.models.executions;

import io.kestra.core.models.tasks.Task;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor
public class NextTaskRun {
    @NotNull
    TaskRun taskRun;

    @NotNull
    Task task;
}
