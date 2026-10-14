package io.kestra.core.runners;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.tasks.Task;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

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
    private WorkerTaskData data;

    /**
     * The id of the task run of the enclosing WorkingDirectory, whose folder the task runs in.
     */
    @Nullable
    private String workingDirectoryId;

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isLegacy() {
        return data == null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String uid() {
        return this.taskRun.getId();
    }

    public static WorkerTaskRunning of(WorkerTask workerTask, WorkerInstance workerInstance) {
        return WorkerTaskRunning.builder()
            .workerInstance(workerInstance)
            .taskRun(workerTask.getTaskRun())
            .task(workerTask.getTask())
            .data(workerTask.getData())
            .workingDirectoryId(workerTask.getWorkingDirectoryId())
            .build();
    }
}