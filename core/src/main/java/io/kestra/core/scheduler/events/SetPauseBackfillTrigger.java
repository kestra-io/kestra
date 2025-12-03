package io.kestra.core.scheduler.events;

import io.kestra.core.events.EventId;
import io.kestra.core.models.triggers.TriggerId;

import java.time.Instant;

/**
 * A command to pause or resume a backfill.
 */
public record SetPauseBackfillTrigger(
    TriggerId id,
    boolean pause,
    Instant timestamp,
    EventId eventId
) implements TriggerEvent {

    public SetPauseBackfillTrigger(TriggerId id, boolean pause) {
        this(id, pause, Instant.now(), EventId.create());
    }
}
