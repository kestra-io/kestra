package io.kestra.executor;

import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.runners.ConcurrencyLimit;
import io.kestra.core.runners.ExecutionRunning;
import io.kestra.core.runners.TransactionContext;
import org.apache.commons.lang3.tuple.Pair;

import java.util.function.BiFunction;

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
}
