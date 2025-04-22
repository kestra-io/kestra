package io.kestra.jdbc.services;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueException;
import io.kestra.core.services.ConcurrencyLimitService;
import io.kestra.jdbc.runner.AbstractJdbcExecutionQueuedStorage;
import io.micronaut.context.annotation.Replaces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@Replaces(ConcurrencyLimitService.class)
public class JdbcConcurrencyLimitService extends ConcurrencyLimitService {

    @Inject
    private AbstractJdbcExecutionQueuedStorage storage;

    @Override
    public Execution unqueue(Execution execution) throws QueueException {
        if (execution.getState().getCurrent() != State.Type.QUEUED) {
            throw new IllegalArgumentException("Only QUEUED execution can be unqueued");
        }

        storage.remove(execution);

        return execution.withState(State.Type.RUNNING);
    }
}
