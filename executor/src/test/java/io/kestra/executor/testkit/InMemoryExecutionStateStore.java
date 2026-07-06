package io.kestra.executor.testkit;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import io.kestra.core.models.executions.Execution;
import io.kestra.executor.ExecutionStateStore;
import io.kestra.executor.ExecutorContext;

/**
 * Map-backed {@link ExecutionStateStore}. {@code lock()} runs the callback synchronously and
 * persists the resulting execution, mirroring the JDBC SELECT FOR UPDATE → apply → UPDATE cycle.
 */
public class InMemoryExecutionStateStore implements ExecutionStateStore {
    private final Map<String, Execution> executions = new ConcurrentHashMap<>();

    @Override
    public synchronized Optional<ExecutorContext> lock(String executionId, Function<Execution, ExecutorContext> function) {
        Execution execution = executions.get(executionId);
        if (execution == null) {
            // not ready for now, skip and wait for a first state (JDBC impl behavior)
            return Optional.empty();
        }

        ExecutorContext result = function.apply(execution);
        if (result == null) {
            return Optional.empty();
        }
        if (result.getExecution() != null) {
            executions.put(executionId, result.getExecution());
        }
        return Optional.of(result);
    }

    @Override
    public Execution create(Execution execution) {
        executions.put(execution.getId(), execution);
        return execution;
    }

    @Override
    public Execution findById(String id) {
        return executions.get(id);
    }

    /**
     * Test seeding/inspection helper — equivalent to what a prior cycle would have persisted.
     */
    public void save(Execution execution) {
        executions.put(execution.getId(), execution);
    }
}
