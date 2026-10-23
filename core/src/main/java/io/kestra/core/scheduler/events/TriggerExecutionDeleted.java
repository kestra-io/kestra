package io.kestra.core.scheduler.events;

import java.time.Instant;
import java.util.Optional;

import io.kestra.core.events.EventId;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.triggers.TriggerId;

/**
 * A non-terminated trigger execution was deleted, so it will never terminate.
 */
public record TriggerExecutionDeleted(
    TriggerId id,
    String executionId,
    Instant timestamp,
    EventId eventId) implements TriggerEvent {

    public TriggerExecutionDeleted(TriggerId id, String executionId) {
        this(id, executionId, Instant.now(), EventId.create());
    }

    /**
     * Creates the deletion event of the given execution.
     *
     * @return the event, or empty if the execution was not started by a trigger whose state the scheduler manages.
     */
    public static Optional<TriggerExecutionDeleted> of(Execution execution) {
        return TriggerExecutionTerminated.managedTriggerId(execution)
            .map(triggerId -> new TriggerExecutionDeleted(triggerId, execution.getId()));
    }
}
