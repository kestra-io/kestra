package io.kestra.executor;

import java.util.Optional;
import java.util.function.Function;

import io.kestra.core.models.executions.Execution;

/**
 * This state store is used by the {@link io.kestra.core.runners.Executor} to lock for processing an execution each time it receives a message.
 * WARNING: it bypasses ACL and tenant checks, so it should not be used somewhere else.
 */
public interface ExecutionStateStore {
    /**
     * Lock an execution for processing using the provided function.
     */
    Optional<ExecutorContext> lock(String executionId, Function<Execution, ExecutorContext> function);

    /**
     * Create an execution.
     */
    Execution create(Execution execution);

    /**
     * Find an execution by its id.
     * WARNING: it bypasses ACL and tenant checks.
     */
    Execution findById(String id);
}
