package io.kestra.scheduler.events;

import io.kestra.core.models.triggers.TriggerId;

import java.time.Instant;

/**
 * A command to delete a backfill.
 */
public record DeleteBackfillTrigger(
    TriggerId id,
    Instant timestamp
) implements TriggerEvent {
    
}
