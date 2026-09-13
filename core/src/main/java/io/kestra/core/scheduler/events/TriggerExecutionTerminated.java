package io.kestra.core.scheduler.events;

import java.time.Instant;

import io.kestra.core.events.EventId;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.triggers.TriggerId;

/**
 * A trigger execution terminated.
 */
public record TriggerExecutionTerminated(
    TriggerId id,
    String executionId,
    State.Type executionState,
    long dispatchEpoch,
    Instant timestamp,
    EventId eventId) implements TriggerEvent {

    public TriggerExecutionTerminated(TriggerId id, String executionId, State.Type executionState) {
        this(id, executionId, executionState, 0L);
    }

    public TriggerExecutionTerminated(TriggerId id, String executionId, State.Type executionState, long dispatchEpoch) {
        this(id, executionId, executionState, dispatchEpoch, Instant.now(), EventId.create());
    }
}
