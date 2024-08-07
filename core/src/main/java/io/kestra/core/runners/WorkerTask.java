package io.kestra.core.runners;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.Task;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.With;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    @JsonDeserialize(using = RawTaskDeserializer.class)
    private Task task;

    @NotNull
    private RunContext runContext;

    public Logger logger() {
        return LoggerFactory.getLogger(
            "flow." + this.getTaskRun().getFlowId() + "." +
                this.getTask().getId()
        );
    }

    @Override
    public String uid() {
        return this.taskRun.getTaskId();
    }

    @Override
    public String taskRunId() {
        return this.taskRun.getId();
    }

    /**
     * This method will fail the tasks with a FAILED or WARNING state depending on the allowFailure attribute of the task.
     *
     * @return this worker task, updated
     */
    public WorkerTask fail() {
        var state = this.task.isAllowFailure() ? State.Type.WARNING : State.Type.FAILED;
        return this.withTaskRun(this.getTaskRun().withState(state));
    }
}
