package io.kestra.executor;

import io.kestra.core.models.executions.Execution;

import java.util.function.Function;

/**
 * This state store is used by the {@link io.kestra.core.runners.Executor} to lock for processing an execution each time it receive a message.
 */
public interface ExecutionStateStore {
    /**
     * Lock an execution for processing using the provided function.
     */
    ExecutorContext lock(String executionId, Function<Execution, ExecutorContext> function);
}
