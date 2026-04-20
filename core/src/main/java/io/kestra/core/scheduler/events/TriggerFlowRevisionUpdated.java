package io.kestra.core.scheduler.events;

import java.time.Instant;

import io.kestra.core.events.EventId;
import io.kestra.core.models.triggers.TriggerId;

/**
 * The flow carrying this trigger was bumped to a new revision, but the trigger
 * definition itself is unchanged. Used to refresh the scheduler's cached flow metadata
 * without mutating the trigger state.
 */
public record TriggerFlowRevisionUpdated(
    TriggerId id,
    int revision,
    Instant timestamp,
    EventId eventId) implements TriggerEvent {

    public TriggerFlowRevisionUpdated(TriggerId id, int revision) {
        this(id, revision, Instant.now(), EventId.create());
    }
}
