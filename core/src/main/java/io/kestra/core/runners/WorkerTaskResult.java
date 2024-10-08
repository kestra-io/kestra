package io.kestra.core.runners;

import io.kestra.core.models.HasUID;
import io.kestra.core.models.executions.TaskRun;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import jakarta.validation.constraints.NotNull;

@Value
@AllArgsConstructor
@Builder
public class WorkerTaskResult implements HasUID {
    @NotNull
    TaskRun taskRun;

    List<TaskRun> dynamicTaskRuns;

    public WorkerTaskResult(TaskRun taskRun) {
        this.taskRun = taskRun;
        this.dynamicTaskRuns = new ArrayList<>();
    }

    public WorkerTaskResult(WorkerTask workerTask) {
        this.taskRun = workerTask.getTaskRun();
        this.dynamicTaskRuns = new ArrayList<>();
    }

    public WorkerTaskResult(WorkerTask workerTask, List<WorkerTaskResult> dynamicWorkerResults) {
        this.taskRun = workerTask.getTaskRun();
        this.dynamicTaskRuns = dynamicWorkerResults
            .stream()
            .map(WorkerTaskResult::getTaskRun)
            .toList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String uid() {
        return taskRun.getId();
    }
}
