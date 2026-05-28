package io.kestra.core.scheduler.events;

import java.time.Instant;

import io.kestra.core.events.EventId;
import io.kestra.core.async.AsyncOperation;
import io.kestra.core.models.triggers.TriggerId;

import jakarta.annotation.Nullable;

/**
 * A command to pause or resume a backfill.
 */
public record SetPauseBackfillTrigger(
    TriggerId id,
    boolean pause,
    Instant timestamp,
    EventId eventId,
    @Nullable String operationId) implements TriggerEvent, AsyncOperation {

    public SetPauseBackfillTrigger(TriggerId id, boolean pause) {
        this(id, pause, Instant.now(), EventId.create(), null);
    }

    public SetPauseBackfillTrigger withOperationId(String operationId) {
        return new SetPauseBackfillTrigger(id, pause, timestamp, eventId, operationId);
    }
}
