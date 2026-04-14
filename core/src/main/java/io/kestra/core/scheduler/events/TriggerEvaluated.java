package io.kestra.core.scheduler.events;

import java.time.Instant;

import io.kestra.core.events.EventId;
import io.kestra.core.models.triggers.TriggerId;
import io.kestra.core.models.triggers.TriggerEvaluationResult;

import jakarta.annotation.Nullable;

/**
 * A trigger was evaluated.
 */
public record TriggerEvaluated(
    TriggerId id,
    @Nullable TriggerEvaluationResult evaluation,
    Instant timestamp,
    EventId eventId) implements TriggerEvent {

    public TriggerEvaluated(TriggerId id, @Nullable TriggerEvaluationResult evaluation) {
        this(id, evaluation, Instant.now(), EventId.create());
    }
}
