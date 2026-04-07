package io.kestra.core.models.executions;

import io.kestra.core.models.flows.State;
import io.kestra.core.queues.event.DispatchEvent;

public record TerminatedLoopExecution(LoopRun loopRun, String executionId, State.Type state) implements DispatchEvent {
    @Override
    public String key() {
        return executionId;
    }

    public String toStringState() {
        return "TerminatedLoopExecution(" +
            "id=" + this.loopRun.taskRunId() +
            ", taskId=" + this.loopRun.taskId() +
            ", value=" + this.loopRun.value() +
            ", index=" + this.loopRun.index() +
            ", state=" + state +
            ")";
    }
}
