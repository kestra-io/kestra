package io.kestra.jdbc.services;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.ConcurrencyLimit;
import io.kestra.core.services.ConcurrencyLimitService;
import io.kestra.jdbc.runner.AbstractJdbcConcurrencyLimitStorage;
import io.kestra.jdbc.runner.AbstractJdbcExecutionQueuedStorage;
import io.kestra.jdbc.runner.JdbcRunnerEnabled;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

@Singleton
@JdbcRunnerEnabled
public class JdbcConcurrencyLimitService implements ConcurrencyLimitService {

    @Inject
    private AbstractJdbcExecutionQueuedStorage executionQueuedStorage;

    @Inject
    private AbstractJdbcConcurrencyLimitStorage concurrencyLimitStorage;

    @Override
    public Execution unqueue(Execution execution, State.Type state) {
        if (execution.getState().getCurrent() != State.Type.QUEUED) {
            throw new IllegalArgumentException("Only QUEUED execution can be unqueued");
        }

        executionQueuedStorage.remove(execution);

        state = (state == null) ? State.Type.RUNNING : state;

        // Validate the target state, throwing an exception if the state is invalid
        if (!VALID_TARGET_STATES.contains(state)) {
            throw new IllegalArgumentException("Invalid target state: " + state + ". Valid states are: " + VALID_TARGET_STATES);
        }

        return execution.withState(state);
    }

    @Override
    public List<ConcurrencyLimit> find(String tenantId) {
        return concurrencyLimitStorage.find(tenantId);
    }

    @Override
    public ConcurrencyLimit update(ConcurrencyLimit concurrencyLimit) {
        return concurrencyLimitStorage.update(concurrencyLimit);
    }

    @Override
    public Optional<ConcurrencyLimit> findById(String tenantId, String namespace, String flowId) {
        return concurrencyLimitStorage.findById(tenantId, namespace, flowId);
    }
}
