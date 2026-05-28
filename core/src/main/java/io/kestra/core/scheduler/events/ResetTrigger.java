package io.kestra.core.scheduler.events;

import java.time.Instant;

import io.kestra.core.events.EventId;
import io.kestra.core.async.AsyncOperation;
import io.kestra.core.models.triggers.TriggerId;

import jakarta.annotation.Nullable;

/**
 * A command to reset a trigger.
 */
public record ResetTrigger(
    TriggerId id,
    Instant timestamp,
    EventId eventId,
    @Nullable String operationId) implements TriggerEvent, AsyncOperation {

    public ResetTrigger(TriggerId id) {
        this(id, Instant.now(), EventId.create(), null);
    }

    public ResetTrigger withOperationId(String operationId) {
        return new ResetTrigger(id, timestamp, eventId, operationId);
    }
}
