package io.kestra.core.scheduler.events;

import io.kestra.core.events.EventId;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.triggers.TriggerId;

import java.time.Instant;

/**
 * A trigger execution terminated.
 */
public record TriggerExecutionTerminated(
    TriggerId id,
    String executionId,
    State.Type executionState,
    Instant timestamp,
    EventId eventId
) implements TriggerEvent {
    
    public TriggerExecutionTerminated(TriggerId id, String executionId, State.Type executionState){
        this(id, executionId, executionState, Instant.now(), EventId.create());
    }
}
