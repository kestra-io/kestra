package io.kestra.core.runners;

import java.util.function.BiConsumer;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.FlowInterface;

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
     * Pop the next queued execution.
     * This method is intended to be part of a larger transaction,
     * see {@link io.kestra.executor.ConcurrencyLimitStateStore#decrementAndPop(FlowInterface, ExecutionQueuedStateStore, BiConsumer)}
     */
    void pop(TransactionContext txContext, String tenantId, String namespace, String flowId, BiConsumer<TransactionContext, Execution> consumer);
}
