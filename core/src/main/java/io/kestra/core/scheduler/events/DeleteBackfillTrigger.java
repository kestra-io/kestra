package io.kestra.core.scheduler.events;

import java.time.Instant;

import io.kestra.core.events.EventId;
import io.kestra.core.async.AsyncOperation;
import io.kestra.core.models.triggers.TriggerId;

import jakarta.annotation.Nullable;

/**
 * A command to delete a backfill.
 */
public record DeleteBackfillTrigger(
    TriggerId id,
    Instant timestamp,
    EventId eventId,
    @Nullable String operationId) implements TriggerEvent, AsyncOperation {

    public DeleteBackfillTrigger(TriggerId id) {
        this(id, Instant.now(), EventId.create(), null);
    }

    public DeleteBackfillTrigger withOperationId(String operationId) {
        return new DeleteBackfillTrigger(id, timestamp, eventId, operationId);
    }
}
