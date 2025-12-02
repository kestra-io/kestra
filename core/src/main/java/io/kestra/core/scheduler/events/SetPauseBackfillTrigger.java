package io.kestra.core.scheduler.events;

import io.kestra.core.models.triggers.TriggerId;

import java.time.Instant;

/**
 * A command to pause or resume a backfill.
 */
public record SetPauseBackfillTrigger(
    TriggerId id,
    Instant timestamp,
    boolean pause
) implements TriggerEvent {
    
    public SetPauseBackfillTrigger(TriggerId id, boolean pause) {
        this(id, Instant.now(), pause);
    }
}
