package org.floworc.core.runners;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.floworc.core.models.executions.TaskRun;
import org.floworc.core.models.flows.State;
import org.floworc.core.models.tasks.Task;

import javax.validation.constraints.NotNull;

@Value
@AllArgsConstructor
public class WorkerTaskResult {
    @NotNull
    private TaskRun taskRun;

    @NotNull
    private Task task;

    public WorkerTaskResult(WorkerTask workerTask, State.Type state) {
        this.taskRun = workerTask.getTaskRun().withState(state);
        this.task = workerTask.getTask();
    }
}
