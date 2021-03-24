package io.kestra.core.runners;

import lombok.Builder;
import lombok.Data;
import lombok.With;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.tasks.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.constraints.NotNull;

@Data
@Builder
public class WorkerTask {
    @NotNull
    @With
    private TaskRun taskRun;

    @NotNull
    private Task task;

    @NotNull
    private RunContext runContext;

    public Logger logger() {
        return LoggerFactory.getLogger(
            "flow." + this.getTaskRun().getFlowId() + "." +
                this.getTask().getId()
        );
    }
}
