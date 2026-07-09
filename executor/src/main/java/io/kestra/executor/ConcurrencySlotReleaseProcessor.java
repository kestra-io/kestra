package io.kestra.executor;

import java.util.List;
import java.util.Optional;

import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Concurrency;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.ExecutionQueuedStateStore;
import io.kestra.core.runners.FlowMetaStoreInterface;
import io.kestra.core.runners.ScopedConcurrencyLimit;
import io.kestra.core.services.ConcurrencyLimitResolver;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Releases a terminated execution's concurrency slots — one per applicable scope: flow,
 * namespace, tenant — and pops the next queued execution that fits all of its own scopes,
 * returning it to the caller instead of emitting it.
 * <p>
 * IMPORTANT — transactional outbox: {@link ConcurrencyLimitStateStore#releaseThenPop} runs its
 * consumer inside the concurrency-limit store's transaction. Queue messages must never be emitted
 * from inside that transaction: on brokers with their own transactionality (e.g. Kafka), a
 * consumer can observe the message before the state-store transaction commits and act on state
 * that does not exist yet. This class therefore deliberately has <b>no queue dependency</b> — it
 * only returns the popped execution, and the caller emits it <b>after</b> this method returns,
 * i.e. after the transaction has committed. Same rule as {@link ExecutionDelayProcessor}.
 */
@Singleton
public class ConcurrencySlotReleaseProcessor {
    private final ConcurrencyLimitStateStore concurrencyLimitStateStore;
    private final ConcurrencyLimitResolver concurrencyLimitResolver;
    private final ExecutionQueuedStateStore executionQueuedStateStore;
    private final FlowMetaStoreInterface flowMetaStore;
    private final MetricRegistry metricRegistry;

    @Inject
    public ConcurrencySlotReleaseProcessor(
        ConcurrencyLimitStateStore concurrencyLimitStateStore,
        ConcurrencyLimitResolver concurrencyLimitResolver,
        ExecutionQueuedStateStore executionQueuedStateStore,
        FlowMetaStoreInterface flowMetaStore,
        MetricRegistry metricRegistry) {
        this.concurrencyLimitStateStore = concurrencyLimitStateStore;
        this.concurrencyLimitResolver = concurrencyLimitResolver;
        this.executionQueuedStateStore = executionQueuedStateStore;
        this.flowMetaStore = flowMetaStore;
        this.metricRegistry = metricRegistry;
    }

    /**
     * Releases the slots held by the terminated execution of {@code executor} in every
     * applicable concurrency scope — a no-op when no limit applies to its flow — and returns
     * the next queued execution, already marked RUNNING, when one was popped. The caller must
     * emit the returned execution only after this method returns — never from inside the
     * state-store transaction (see the class Javadoc).
     */
    public Optional<Execution> release(ExecutorContext executor) {
        List<ScopedConcurrencyLimit> limits = concurrencyLimitResolver.resolveLimits(executor.getFlow());
        if (limits.isEmpty()) {
            return Optional.empty();
        }

        Execution execution = executor.getExecution();

        // if an execution was queued but never running, it would have never been counted inside the concurrency limit and should not lead to popping a new queued execution
        boolean queuedThenKilled = execution.getState().getCurrent() == State.Type.KILLED
            && execution.getState().getHistories().stream().anyMatch(h -> h.getState().isQueued())
            && execution.getState().getHistories().stream().noneMatch(h -> h.getState().onlyRunning());
        // if an execution was FAILED or CANCELLED due to concurrency limit exceeded, it would have never been counter inside the concurrency limit and should not lead to popping a new queued execution
        boolean concurrencyShortCircuitState = Concurrency.possibleTransitions(execution.getState().getCurrent())
            && execution.getState().getHistories().get(execution.getState().getHistories().size() - 2).getState().isCreated();
        // as we may receive multiple time killed execution (one when we kill it, then one for each running worker task), we limit to the first we receive: when the state transitioned from KILLING to KILLED
        boolean killingThenKilled = execution.getState().getCurrent().isKilled() && executor.getOriginalState() == State.Type.KILLING;
        if (!queuedThenKilled && !concurrencyShortCircuitState && (!execution.getState().getCurrent().isKilled() || killingThenKilled)) {
            // Pop the next queued execution atomically with the decrements/increment to avoid race conditions
            // that could leave executions stuck in the queue indefinitely (see issue #13785)
            return concurrencyLimitStateStore.releaseThenPop(
                executor.getFlow(),
                limits,
                executionQueuedStateStore,
                candidate -> flowMetaStore.findByExecution(candidate)
                    .map(concurrencyLimitResolver::resolveLimits)
                    .orElse(List.of()),
                (txContext, queued) ->
                {
                    var newExecution = queued.withState(State.Type.RUNNING);
                    metricRegistry.counter(
                        MetricRegistry.METRIC_EXECUTOR_EXECUTION_POPPED_COUNT, MetricRegistry.METRIC_EXECUTOR_EXECUTION_POPPED_COUNT_DESCRIPTION,
                        metricRegistry.tags(newExecution)
                    ).increment();
                    return newExecution;
                }
            );
        }

        return Optional.empty();
    }
}
