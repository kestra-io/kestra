package org.floworc.core.executions;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.Wither;
import org.floworc.core.tasks.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.constraints.NotNull;

@Value
@Builder
public class WorkerTask {
    @NotNull
    @Wither
    private TaskRun taskRun;

    @NotNull
    private Task task;

    public Logger logger() {
        return LoggerFactory.getLogger(
            "flow." + this.getTaskRun().getFlowId() + "." +
                this.getTask().getId()
        );
    }
}
