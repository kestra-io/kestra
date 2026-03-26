package io.kestra.core.scheduler.events;

import java.time.Instant;

import io.kestra.core.events.EventId;
import io.kestra.core.models.triggers.TriggerId;

/**
 * A command to disable/enable a trigger.
 */
public record SetDisableTrigger(
    TriggerId id,
    boolean disabled,
    Instant timestamp,
    EventId eventId) implements TriggerEvent {

    public SetDisableTrigger(TriggerId id, Boolean disabled) {
        this(id, disabled, Instant.now(), EventId.create());
    }
}
