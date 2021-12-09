package io.kestra.core.runners;

import io.kestra.core.models.executions.TaskRun;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

import javax.validation.constraints.NotNull;

@Value
@AllArgsConstructor
@Builder
public class WorkerTaskResult {
    @NotNull
    TaskRun taskRun;

    public WorkerTaskResult(WorkerTask workerTask) {
        this.taskRun = workerTask.getTaskRun();
    }
}
