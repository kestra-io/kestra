package org.kestra.core.models.executions;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.kestra.core.models.tasks.Task;

import javax.validation.constraints.NotNull;

@Value
@AllArgsConstructor
public class NextTaskRun {
    @NotNull
    TaskRun taskRun;

    @NotNull
    Task task;
}
