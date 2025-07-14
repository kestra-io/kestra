package io.kestra.core.services;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueException;
import jakarta.inject.Singleton;

import java.util.EnumSet;
import java.util.Set;

@Singleton
public class ConcurrencyLimitService {

    protected static final Set<State.Type> VALID_TARGET_STATES =
        EnumSet.of(State.Type.RUNNING, State.Type.CANCELLED, State.Type.FAILED);

    /**
     * Unqueue a queued execution.
     *
     * @throws IllegalArgumentException in case the execution is not queued.
     */
    public Execution unqueue(Execution execution, State.Type state) throws QueueException {
        if (execution.getState().getCurrent() != State.Type.QUEUED) {
            throw new IllegalArgumentException("Only QUEUED execution can be unqueued");
        }

        state = (state == null) ? State.Type.RUNNING : state;

        // Validate the target state, throwing an exception if the state is invalid
        if (!VALID_TARGET_STATES.contains(state)) {
            throw new IllegalArgumentException("Invalid target state: " + state + ". Valid states are: " + VALID_TARGET_STATES);
        }

        return execution.withState(state);
    }
}
