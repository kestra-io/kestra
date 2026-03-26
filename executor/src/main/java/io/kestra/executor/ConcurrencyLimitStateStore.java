package io.kestra.executor;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import org.apache.commons.lang3.tuple.Pair;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.runners.ConcurrencyLimit;
import io.kestra.core.runners.ExecutionQueuedStateStore;
import io.kestra.core.runners.ExecutionRunning;
import io.kestra.core.runners.TransactionContext;

/**
 * This state store is used by the {@link io.kestra.core.runners.Executor} to handle flow concurrency limit.
 */
public interface ConcurrencyLimitStateStore {
    /**
     * Count running executions, then process the concurrency limit with the provided consumer.
     *
     * @implNote Implementors must use some sort of transaction (FOR UPDATE SKIP LOCKED or {@link io.kestra.core.lock.LockService#tryLock(String, String, Runnable)})
     *           for accuracy.
     */
    ExecutionRunning countThenProcess(FlowInterface flow, BiFunction<TransactionContext, ConcurrencyLimit, Pair<ExecutionRunning, ConcurrencyLimit>> consumer);

    /**
     * Decrement a flow concurrency limit.
     *
     * @return the new concurrency limit value.
     */
    int decrement(FlowInterface flow);

    /**
     * Increment a flow concurrency limit.
     *
     * @implNote Implementors that support transaction must use the provided {@link TransactionContext} to attach to the current transaction.
     */
    void increment(TransactionContext txContext, FlowInterface flow);

    /**
     * Atomically decrement the concurrency limit counter and pop a queued execution if available.
     * This ensures the decrement, limit check, and pop all happen within the same transaction,
     * preventing race conditions that could leave executions stuck in queue indefinitely.
     *
     * @param flow the flow to decrement the counter for
     * @param executionQueuedStateStore the storage to pop from
     * @param consumer the consumer to call with the popped execution (only called if pop succeeds and limit allows)
     */
    void decrementAndPop(FlowInterface flow, ExecutionQueuedStateStore executionQueuedStateStore, BiConsumer<TransactionContext, Execution> consumer);
}
