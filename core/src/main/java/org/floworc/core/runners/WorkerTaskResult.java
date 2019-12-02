package org.floworc.core.runners;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.floworc.core.models.executions.TaskRun;
import org.floworc.core.models.tasks.Task;

import javax.validation.constraints.NotNull;

@Value
@AllArgsConstructor
public class WorkerTaskResult {
    @NotNull
    private TaskRun taskRun;

    @NotNull
    private Task task;

    public WorkerTaskResult(WorkerTask workerTask) {
        this.taskRun = workerTask.getTaskRun();
        this.task = workerTask.getTask();
    }
}
