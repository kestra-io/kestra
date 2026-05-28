package io.kestra.core.scheduler.events;

import java.time.Instant;

import io.kestra.core.events.EventId;
import io.kestra.core.async.AsyncOperation;
import io.kestra.core.models.triggers.TriggerId;

import jakarta.annotation.Nullable;

/**
 * An existing trigger was deleted (i.e. removed from a flow).
 */
public record TriggerDeleted(
    TriggerId id,
    Instant timestamp,
    EventId eventId,
    @Nullable String operationId) implements TriggerEvent, AsyncOperation {
    public TriggerDeleted(TriggerId id) {
        this(id, Instant.now(), EventId.create(), null);
    }

    public TriggerDeleted withOperationId(String operationId) {
        return new TriggerDeleted(id, timestamp, eventId, operationId);
    }
}
