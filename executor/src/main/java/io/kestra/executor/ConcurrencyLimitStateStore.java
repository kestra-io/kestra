package io.kestra.executor;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Concurrency;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.runners.ConcurrencyLimit;
import io.kestra.core.runners.ExecutionQueuedStateStore;
import io.kestra.core.runners.ExecutionRunning;
import io.kestra.core.runners.ScopedConcurrencyLimit;
import io.kestra.core.runners.TransactionContext;

/**
 * This state store is used by the {@link io.kestra.core.runners.Executor} to handle flow concurrency limit.
 */
public interface ConcurrencyLimitStateStore {
    Logger LOG = LoggerFactory.getLogger(ConcurrencyLimitStateStore.class);

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

    /**
     * Atomically read the running counter of every given scope, hand the counts (in scope
     * order) to the decision function, and — when it asks to claim — increment every scope
     * counter within the same transaction. The consumer receives the transaction context so it
     * can attach related writes (e.g. saving a queued execution) to the same transaction.
     * <p>
     * The default implementation only supports the single flow-scoped limit case (the OSS
     * semantics) and delegates to {@link #countThenProcess(FlowInterface, BiFunction)};
     * implementers must override it to support namespace or tenant scoped limits.
     *
     * @param flow the flow whose execution is evaluated
     * @param limits the applicable limits in evaluation order, never empty
     * @param consumer decides from the running counts; returns the processed execution and
     *        whether to claim one slot in every scope
     */
    default ExecutionRunning countThenProcess(FlowInterface flow, List<ScopedConcurrencyLimit> limits,
        BiFunction<TransactionContext, List<Integer>, Pair<ExecutionRunning, Boolean>> consumer) {
        if (limits.size() == 1 && limits.getFirst().scope() == ScopedConcurrencyLimit.Scope.FLOW) {
            return countThenProcess(flow, (txContext, concurrencyLimit) ->
            {
                int running = concurrencyLimit.getRunning() == null ? 0 : concurrencyLimit.getRunning();
                Pair<ExecutionRunning, Boolean> result = consumer.apply(txContext, List.of(running));
                return Pair.of(result.getLeft(), Boolean.TRUE.equals(result.getRight()) ? concurrencyLimit.withRunning(running + 1) : concurrencyLimit);
            });
        }
        throw new UnsupportedOperationException("This state store only supports flow-scoped concurrency limits");
    }

    /**
     * Atomically release the slots a terminated execution holds — decrement the counter of
     * every given scope — then pop the oldest queued execution that fits <b>all</b> of its own
     * scopes, skipping candidates that do not fit yet. The popped execution's scope counters
     * are incremented in the same transaction and the mapped execution is returned; the caller
     * must emit it only after this method returns, never from inside the transaction.
     * <p>
     * The default implementation only supports the single flow-scoped limit case (the OSS
     * semantics): for the {@link Concurrency.Behavior#QUEUE} behavior it delegates to
     * {@link #decrementAndPop(FlowInterface, ExecutionQueuedStateStore, BiConsumer)}, otherwise
     * to {@link #decrement(FlowInterface)}. Implementors must override it to support namespace
     * or tenant scoped limits.
     *
     * @param flow the flow of the terminated execution
     * @param limits the limits applying to the terminated execution, in evaluation order, never empty
     * @param executionQueuedStateStore the store queued candidates are popped from
     * @param candidateLimits resolves the limits applying to a queued candidate (its flow may
     *        differ from {@code flow} when namespace or tenant scopes are involved)
     * @param consumer maps the popped execution to the execution to run (e.g. marks it RUNNING)
     * @return the mapped popped execution, when one was popped
     */
    default Optional<Execution> releaseThenPop(
        FlowInterface flow,
        List<ScopedConcurrencyLimit> limits,
        ExecutionQueuedStateStore executionQueuedStateStore,
        Function<Execution, List<ScopedConcurrencyLimit>> candidateLimits,
        BiFunction<TransactionContext, Execution, Execution> consumer) {
        if (limits.size() == 1 && limits.getFirst().scope() == ScopedConcurrencyLimit.Scope.FLOW) {
            if (limits.getFirst().concurrency() == null) {
                // the flow limit was removed while the admitted execution ran: still release
                // the slot it claimed — the counter must not leak
                decrement(flow);
                return Optional.empty();
            }

            if (limits.getFirst().concurrency().getBehavior() == Concurrency.Behavior.QUEUE) {
                AtomicReference<Execution> popped = new AtomicReference<>();
                decrementAndPop(flow, executionQueuedStateStore, (txContext, queued) -> popped.set(consumer.apply(txContext, queued)));
                return Optional.ofNullable(popped.get());
            }

            int newLimit = decrement(flow);
            if (newLimit >= limits.getFirst().concurrency().getLimit()) {
                LOG.error(
                    "Concurrency limit reached for flow {}.{} after decrementing the execution running count due to a terminated execution. This should not happen.",
                    flow.getNamespace(), flow.getId()
                );
            }
            return Optional.empty();
        }
        throw new UnsupportedOperationException("This state store only supports flow-scoped concurrency limits");
    }
}
