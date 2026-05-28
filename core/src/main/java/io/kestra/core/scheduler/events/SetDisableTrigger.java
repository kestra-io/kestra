package io.kestra.core.scheduler.events;

import java.time.Instant;

import io.kestra.core.events.EventId;
import io.kestra.core.async.AsyncOperation;
import io.kestra.core.models.triggers.TriggerId;

import jakarta.annotation.Nullable;

/**
 * A command to disable/enable a trigger.
 */
public record SetDisableTrigger(
    TriggerId id,
    boolean disabled,
    Instant timestamp,
    EventId eventId,
    @Nullable String operationId) implements TriggerEvent, AsyncOperation {

    public SetDisableTrigger(TriggerId id, Boolean disabled) {
        this(id, disabled, Instant.now(), EventId.create(), null);
    }

    public SetDisableTrigger withOperationId(String operationId) {
        return new SetDisableTrigger(id, disabled, timestamp, eventId, operationId);
    }
}
