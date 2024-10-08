package io.kestra.core.runners;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.tasks.Task;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotNull;

@Data
@SuperBuilder
@NoArgsConstructor
public class WorkerTaskRunning extends WorkerJobRunning {
    public static final String TYPE = "task";

    @NotNull
    @JsonInclude
    private final String type = TYPE;

    @NotNull
    private TaskRun taskRun;

    @NotNull
    private Task task;

    @NotNull
    private RunContext runContext;

    /**
     * {@inheritDoc}
     */
    @Override
    public String uid() {
        return this.taskRun.getId();
    }

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