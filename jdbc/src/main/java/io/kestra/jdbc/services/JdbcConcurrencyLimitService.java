package io.kestra.jdbc.services;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueException;
import io.kestra.core.services.ConcurrencyLimitService;
import io.kestra.jdbc.runner.AbstractJdbcExecutionQueuedStorage;
import io.kestra.jdbc.runner.JdbcRunnerEnabled;
import io.micronaut.context.annotation.Replaces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@JdbcRunnerEnabled
@Replaces(ConcurrencyLimitService.class)
public class JdbcConcurrencyLimitService extends ConcurrencyLimitService {

    @Inject
    private AbstractJdbcExecutionQueuedStorage storage;

    @Override
    public Execution unqueue(Execution execution, State.Type state) throws QueueException {
        if (execution.getState().getCurrent() != State.Type.QUEUED) {
            throw new IllegalArgumentException("Only QUEUED execution can be unqueued");
        }

        storage.remove(execution);

        state = (state == null) ? State.Type.RUNNING : state;

        // Validate the target state, throwing an exception if the state is invalid
        if (!VALID_TARGET_STATES.contains(state)) {
            throw new IllegalArgumentException("Invalid target state: " + state + ". Valid states are: " + VALID_TARGET_STATES);
        }

        return execution.withState(state);
    }
}
