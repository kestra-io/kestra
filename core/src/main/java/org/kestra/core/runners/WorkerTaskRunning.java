package org.kestra.core.runners;

import lombok.Builder;
import lombok.Data;
import org.kestra.core.models.executions.TaskRun;
import org.kestra.core.models.tasks.Task;

import javax.validation.constraints.NotNull;

@Data
@Builder
public class WorkerTaskRunning {
    @NotNull
    private TaskRun taskRun;

    @NotNull
    private Task task;

    @NotNull
    private RunContext runContext;

    @NotNull
    private WorkerInstance workerInstance;

    @NotNull
    private int partition;

    public static WorkerTaskRunning of(WorkerTask workerTask, WorkerInstance workerInstance, int partition) {
        return WorkerTaskRunning.builder()
            .workerInstance(workerInstance)
            .partition(partition)
            .taskRun(workerTask.getTaskRun())
            .task(workerTask.getTask())
            .runContext(workerTask.getRunContext())
            .build();
    }
}
