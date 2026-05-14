package io.kestra.core.models.executions;

import io.kestra.core.models.flows.State;
import io.kestra.core.queues.event.DispatchEvent;
import jakarta.annotation.Nullable;

import java.util.Map;

/**
 * Event emitted by the executor to communicate a loop sub-execution state change to its parent execution.
 * The {@code state} field drives how the parent reacts: a {@link State.Type#PAUSED} state pauses the
 * parent loop task run; any terminated state ends or advances the loop iteration.
 */
public record LoopExecutionEvent(
    LoopRun loopRun,
    String executionId,
    State.Type state,
    @Nullable Map<String, Object> outputs
) implements DispatchEvent {

    @Override
    public String key() {
        return executionId;
    }

    public String toStringState() {
        return "LoopExecutionEvent(" +
            "id=" + this.loopRun.taskRunId() +
            ", taskId=" + this.loopRun.taskId() +
            ", value=" + this.loopRun.value() +
            ", index=" + this.loopRun.index() +
            ", state=" + state +
            ")";
    }
}
