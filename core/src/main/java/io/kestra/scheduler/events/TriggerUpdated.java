package io.kestra.scheduler.events;

import io.kestra.core.models.triggers.TriggerId;

import java.time.Instant;

/**
 * An existing trigger was updated.
 */
public record TriggerUpdated(
    TriggerId id,
    int revision,
    Instant timestamp
) implements TriggerEvent {
    public TriggerUpdated(TriggerId id, int revision) {
        this(id, revision, Instant.now());
    }
}
