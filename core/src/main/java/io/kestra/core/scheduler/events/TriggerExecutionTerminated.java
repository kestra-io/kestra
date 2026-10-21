package io.kestra.core.scheduler.events;

import java.time.Instant;
import java.util.Optional;

import io.kestra.core.events.EventId;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.triggers.TriggerId;
import io.kestra.plugin.core.flow.Subflow;
import io.kestra.plugin.core.trigger.Flow;
import io.kestra.plugin.core.trigger.Webhook;

/**
 * A trigger execution terminated.
 */
public record TriggerExecutionTerminated(
    TriggerId id,
    String executionId,
    State.Type executionState,
    long dispatchEpoch,
    Instant timestamp,
    EventId eventId) implements TriggerEvent {

    public TriggerExecutionTerminated(TriggerId id, String executionId, State.Type executionState) {
        this(id, executionId, executionState, 0L);
    }

    public TriggerExecutionTerminated(TriggerId id, String executionId, State.Type executionState, long dispatchEpoch) {
        this(id, executionId, executionState, dispatchEpoch, Instant.now(), EventId.create());
    }

    /**
     * Creates the termination event of the given execution in its current state.
     *
     * @return the event, or empty if the execution was not started by a trigger whose state the scheduler manages.
     */
    public static Optional<TriggerExecutionTerminated> of(Execution execution) {
        return managedTriggerId(execution)
            .map(triggerId -> new TriggerExecutionTerminated(triggerId, execution.getId(), execution.getState().getCurrent()));
    }

    /**
     * @return the id of the trigger that started the given execution, or empty if the scheduler doesn't manage its state.
     */
    static Optional<TriggerId> managedTriggerId(Execution execution) {
        if (execution.getTrigger() == null) {
            return Optional.empty();
        }
        // The scheduler didn't manage states for the WebHook and the Flow trigger
        String type = execution.getTrigger().getType();
        if (Webhook.class.getName().equals(type) || Flow.class.getName().equals(type) || Subflow.class.getName().equals(type)) {
            return Optional.empty();
        }
        return Optional.of(TriggerId.of(execution.getTenantId(), execution.getNamespace(), execution.getFlowId(), execution.getTrigger().getId()));
    }
}
