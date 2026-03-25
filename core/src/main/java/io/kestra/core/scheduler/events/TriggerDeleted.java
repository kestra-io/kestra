package io.kestra.core.scheduler.events;

import java.time.Instant;

import io.kestra.core.events.EventId;
import io.kestra.core.models.triggers.TriggerId;

/**
 * An existing trigger was deleted (i.e. removed from a flow).
 */
public record TriggerDeleted(
    TriggerId id,
    Instant timestamp,
    EventId eventId) implements TriggerEvent {
    public TriggerDeleted(TriggerId id) {
        this(id, Instant.now(), EventId.create());
    }
}
