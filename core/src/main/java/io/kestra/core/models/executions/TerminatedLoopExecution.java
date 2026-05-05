package io.kestra.core.models.executions;

import io.kestra.core.models.flows.State;
import io.kestra.core.queues.event.DispatchEvent;

import java.util.Map;

public record TerminatedLoopExecution(LoopRun loopRun, String executionId, State.Type state, Map<String, Object> outputs) implements DispatchEvent {
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
