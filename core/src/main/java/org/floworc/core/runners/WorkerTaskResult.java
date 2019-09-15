package org.floworc.core.runners;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.floworc.core.models.executions.TaskRun;
import org.floworc.core.models.flows.State;
import org.floworc.core.models.tasks.Task;

@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@Data
public class WorkerTaskResult extends WorkerTask {
    public WorkerTaskResult(TaskRun taskRun, Task task) {
        super(taskRun, task);
    }

    public WorkerTaskResult(WorkerTask workerTask, State.Type state) {
        super(workerTask.getTaskRun().withState(state), workerTask.getTask());
    }
}
