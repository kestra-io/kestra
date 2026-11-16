package io.kestra.core.runners;

import java.util.List;
import java.util.function.BiConsumer;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.FlowInterface;

import io.micronaut.core.annotation.Nullable;

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
     * Count queued executions for a flow.
     *
     * @implNote Implementors that support transaction must use the provided {@link TransactionContext} to attach to the current transaction.
     */
    int count(TransactionContext txContext, String tenantId, String namespace, String flowId);

    /**
     * Count queued executions within a scope, outside of any transaction: a single flow when {@code flowId} is set,
     * a namespace and its descendants when only {@code namespace} is set, the whole tenant when both are null.
     * A {@code flowId} requires its {@code namespace}.
     */
    int count(String tenantId, @Nullable String namespace, @Nullable String flowId);

    /**
     * Pop the next queued execution.
     * This method is intended to be part of a larger transaction,
     * see {@link io.kestra.executor.ConcurrencyLimitStateStore#decrementAndPop(FlowInterface, ExecutionQueuedStateStore, BiConsumer)}
     */
    void pop(TransactionContext txContext, String tenantId, String namespace, String flowId, BiConsumer<TransactionContext, Execution> consumer);

    /**
     * Every queued execution across all tenants, oldest first; used to resubmit them all from the CLI.
     */
    List<ExecutionQueued> getAllForAllTenants();
}
