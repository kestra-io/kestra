package io.kestra.core.scheduler.events;

import java.time.Instant;

import io.kestra.core.events.EventId;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.triggers.TriggerId;

/**
 * A trigger was executed.
 */
public record TriggerEvaluated(
    TriggerId id,
    // TODO we could have a dedicated class to simplify the model
    Execution execution,
    Instant timestamp,
    EventId eventId) implements TriggerEvent {

    public TriggerEvaluated(TriggerId id, Execution execution) {
        this(id, execution, Instant.now(), EventId.create());
    }

}
