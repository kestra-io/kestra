package io.kestra.core.scheduler.events;

import java.time.Instant;

import io.kestra.core.events.EventId;
import io.kestra.core.models.triggers.TriggerId;

/**
 * An existing trigger was updated.
 */
public record TriggerUpdated(
    TriggerId id,
    int revision,
    Instant timestamp,
    EventId eventId) implements TriggerEvent {
    public TriggerUpdated(TriggerId id, int revision) {
        this(id, revision, Instant.now(), EventId.create());
    }
}
