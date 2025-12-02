package io.kestra.core.scheduler.events;

import io.kestra.core.models.triggers.TriggerId;

import java.time.Instant;

/**
 * A new trigger was created (i.e. added to a flow).
 */
public record TriggerCreated(
    TriggerId id,
    Instant timestamp,
    int revision
) implements TriggerEvent {
    
    public TriggerCreated(TriggerId id, int revision) {
        this(id, Instant.now(), revision);
    }
}
