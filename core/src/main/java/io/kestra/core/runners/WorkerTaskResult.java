package io.kestra.core.runners;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.tasks.Task;

import javax.validation.constraints.NotNull;

@Value
@AllArgsConstructor
@Builder
public class WorkerTaskResult {
    @NotNull
    TaskRun taskRun;

    @NotNull
    Task task;

    public WorkerTaskResult(WorkerTask workerTask) {
        this.taskRun = workerTask.getTaskRun();
        this.task = workerTask.getTask();
    }
}
