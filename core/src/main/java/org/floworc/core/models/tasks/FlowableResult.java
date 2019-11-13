package org.floworc.core.models.tasks;

import lombok.Builder;
import lombok.Value;
import org.floworc.core.models.executions.TaskRun;
import org.floworc.core.models.flows.State;

import java.util.List;

@Builder
@Value
public class FlowableResult {
    private Result result;

    private List<TaskRun> nexts;

    private State.Type childState;

    private Task childTask;

    private TaskRun childTaskRun;

    public enum Result {
        NEXTS,
        WAIT,
        ENDED
    }
}
