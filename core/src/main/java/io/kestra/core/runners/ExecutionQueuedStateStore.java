package io.kestra.core.runners;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.runners.ExecutionQueued;

import java.util.function.BiConsumer;

/**
 * This state store is used by the {@link Executor} to handle execution queued by flow concurrency limit.
 */
public interface ExecutionQueuedStateStore {
    /**
     * remove a queued execution.
     */
    void remove(Execution execution);

    /**
     * Save a queued execution.
     *
     * @implNote Implementors that support transaction must use the provided {@link TransactionContext} to attach to the current transaction.
     */
    void save(TransactionContext txContext, ExecutionQueued executionQueued);

    /**
     * Pop a queued execution: remove the oldest one and process it with the provided consumer.
     */
    void pop(String tenantId, String namespace, String flowId, BiConsumer<TransactionContext, Execution> consumer);
}
