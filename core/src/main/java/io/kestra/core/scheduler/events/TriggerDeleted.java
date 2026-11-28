package io.kestra.core.scheduler.events;

import java.time.Instant;

import io.kestra.core.async.AsyncOperation;
import io.kestra.core.events.EventId;
import io.kestra.core.models.triggers.TriggerId;

import jakarta.annotation.Nullable;

/**
 * An existing trigger was deleted (i.e. removed from a flow).
 *
 * @param revision the flow revision that removed the trigger, or {@code null} for a deletion that is not tied to a
 *                 flow revision, which is then applied whatever revision the trigger state comes from.
 */
public record TriggerDeleted(
    TriggerId id,
    @Nullable Integer revision,
    Instant timestamp,
    EventId eventId,
    @Nullable String operationId) implements TriggerEvent, AsyncOperation {
    public TriggerDeleted(TriggerId id) {
        this(id, null, Instant.now(), EventId.create(), null);
    }

    public TriggerDeleted(TriggerId id, int revision) {
        this(id, revision, Instant.now(), EventId.create(), null);
    }

    public TriggerDeleted withOperationId(String operationId) {
        return new TriggerDeleted(id, revision, timestamp, eventId, operationId);
    }
}
