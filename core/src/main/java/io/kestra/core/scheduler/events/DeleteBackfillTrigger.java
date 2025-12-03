package io.kestra.core.scheduler.events;

import io.kestra.core.events.EventId;
import io.kestra.core.models.triggers.TriggerId;

import java.time.Instant;

/**
 * A command to delete a backfill.
 */
public record DeleteBackfillTrigger(
    TriggerId id,
    Instant timestamp,
    EventId eventId
) implements TriggerEvent {
    
    public DeleteBackfillTrigger(TriggerId id) {
        this(id, Instant.now(), EventId.create());
    }
}
