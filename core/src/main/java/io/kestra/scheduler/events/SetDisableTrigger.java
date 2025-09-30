package io.kestra.scheduler.events;

import io.kestra.core.models.triggers.TriggerId;

import java.time.Instant;

/**
 * A command to disable/enable a trigger.
 */
public record SetDisableTrigger(
    TriggerId id,
    Instant timestamp,
    boolean disabled
) implements TriggerEvent {
    
    public SetDisableTrigger(TriggerId id, Boolean disabled) {
        this(id, Instant.now(), disabled);
    }
}
