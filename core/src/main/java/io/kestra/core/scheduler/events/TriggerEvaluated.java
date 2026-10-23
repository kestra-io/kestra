package io.kestra.core.scheduler.events;

import java.time.Instant;

import io.kestra.core.events.EventId;
import io.kestra.core.models.triggers.TriggerEvaluationResult;
import io.kestra.core.models.triggers.TriggerId;

import jakarta.annotation.Nullable;

/**
 * A trigger was evaluated.
 */
public record TriggerEvaluated(
    TriggerId id,
    @Nullable TriggerEvaluationResult evaluation,
    Instant timestamp,
    EventId eventId,
    @Nullable Long dispatchEpoch) implements TriggerEvent {

    public TriggerEvaluated(TriggerId id, @Nullable TriggerEvaluationResult evaluation) {
        this(id, evaluation, Instant.now(), EventId.create(), null);
    }

    public TriggerEvaluated(TriggerId id, @Nullable TriggerEvaluationResult evaluation, Instant timestamp, EventId eventId) {
        this(id, evaluation, timestamp, eventId, null);
    }

    public TriggerEvaluated(TriggerId id, @Nullable TriggerEvaluationResult evaluation, long dispatchEpoch) {
        this(id, evaluation, Instant.now(), EventId.create(), dispatchEpoch);
    }

    public TriggerEvaluated(TriggerId id, @Nullable TriggerEvaluationResult evaluation, long dispatchEpoch, EventId eventId) {
        this(id, evaluation, Instant.now(), eventId, dispatchEpoch);
    }
}
