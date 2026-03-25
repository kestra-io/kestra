package io.kestra.core.scheduler.events;

import java.time.Instant;

import io.kestra.core.events.EventId;
import io.kestra.core.models.triggers.TriggerId;

/**
 * A new trigger was created (i.e. added to a flow).
 */
public record TriggerCreated(
    TriggerId id,
    int revision,
    Instant timestamp,
    EventId eventId) implements TriggerEvent {

    public TriggerCreated(TriggerId id, int revision) {
        this(id, revision, Instant.now(), EventId.create());
    }
}
