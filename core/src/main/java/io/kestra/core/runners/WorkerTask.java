package io.kestra.core.runners;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.kestra.core.models.executions.ExecutionKind;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.Task;
import jakarta.annotation.Nullable;
import lombok.Builder;
import lombok.Data;
import lombok.With;

import jakarta.validation.constraints.NotNull;

@Data
@Builder
public class WorkerTask extends WorkerJob {
    public static final String TYPE = "task";

    @NotNull
    @JsonInclude
    private final String type = TYPE;

    @NotNull
    @With
    private TaskRun taskRun;

    @NotNull
    private Task task;

    @NotNull
    private RunContext runContext;

    @Nullable
    private ExecutionKind  executionKind;

    /**
     * {@inheritDoc}
     */
    @Override
    public String uid() {
        return this.taskRun.getId();
    }

    /**
     * This method will fail the tasks with a FAILED or WARNING state depending on the allowFailure attribute of the task.
     *
     * @return this worker task, updated
     */
    public TaskRun fail() {
        var state = State.Type.fail(task);
        return this.getTaskRun().withState(state);
    }
}
