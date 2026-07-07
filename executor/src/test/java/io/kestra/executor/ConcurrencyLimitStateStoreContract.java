package io.kestra.executor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Concurrency;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.runners.ExecutionQueued;
import io.kestra.core.runners.ExecutionQueuedStateStore;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.core.log.Log;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Behavioral contract every {@link ConcurrencyLimitStateStore} implementation must honor —
 * annotation-free so it can run against both container-injected stores (JDBC through
 * {@link AbstractConcurrencyLimitStateStoreTest}) and hand-built ones (the executor testkit's
 * in-memory fake), keeping fakes provably faithful to the production implementations.
 * Subclasses provide the stores under test via {@link #store()} and {@link #queuedStore()};
 * both must share the same backend so {@code decrementAndPop} spans them transactionally.
 */
public abstract class ConcurrencyLimitStateStoreContract {

    /** The {@link ConcurrencyLimitStateStore} implementation under contract. */
    protected abstract ConcurrencyLimitStateStore store();

    /** The {@link ExecutionQueuedStateStore} of the same backend, popped by {@code decrementAndPop}. */
    protected abstract ExecutionQueuedStateStore queuedStore();

    protected static Flow flow(int limit) {
        return Flow.builder()
            .tenantId("main")
            .namespace("io.kestra.unittest")
            .id(IdUtils.create())
            .revision(1)
            .concurrency(Concurrency.builder().behavior(Concurrency.Behavior.QUEUE).limit(limit).build())
            .tasks(List.of(Log.builder().id("log").type(Log.class.getName()).message("test").build()))
            .build();
    }

    @Test
    void shouldCountZeroWhenFlowNeverRan() {
        assertThat(currentCount(flow(1))).isZero();
    }

    @Test
    void shouldPersistCounterReturnedByCountThenProcess() {
        // Given
        Flow flow = flow(2);

        // When: the consumer claims two running slots
        store().countThenProcess(flow, (txContext, limit) -> Pair.of(null, limit.withRunning(2)));

        // Then
        assertThat(currentCount(flow)).isEqualTo(2);
    }

    @Test
    void shouldDecrementCounterAndFloorAtZero() {
        // Given
        Flow flow = flow(2);
        store().countThenProcess(flow, (txContext, limit) -> Pair.of(null, limit.withRunning(1)));

        // When / Then
        assertThat(store().decrement(flow)).isZero();
        assertThat(store().decrement(flow)).isZero();
        assertThat(currentCount(flow)).isZero();
    }

    @Test
    void shouldPopOldestQueuedExecutionAndHandItTheFreedSlot() {
        // Given: one running execution at the limit, two queued behind it (older first)
        Flow flow = flow(1);
        Execution first = Execution.newExecution(flow, List.of());
        Execution second = Execution.newExecution(flow, List.of());
        Instant now = Instant.now();
        store().countThenProcess(flow, (txContext, limit) -> {
            // queued entries are saved inside the counting transaction, exactly as
            // ExecutionEventMessageHandler does when the QUEUE behavior trips
            queuedStore().save(txContext, queued(flow, first, now.minusSeconds(60)));
            queuedStore().save(txContext, queued(flow, second, now));
            return Pair.of(null, limit.withRunning(1));
        });

        // When: the running execution terminates and frees its slot, twice
        List<Execution> popped = new ArrayList<>();
        store().decrementAndPop(flow, queuedStore(), (txContext, execution) -> popped.add(execution));
        store().decrementAndPop(flow, queuedStore(), (txContext, execution) -> popped.add(execution));

        // Then: FIFO by date, and each popped execution took over the freed slot (counter re-incremented)
        assertThat(popped).extracting(Execution::getId).containsExactly(first.getId(), second.getId());
        assertThat(currentCount(flow)).isEqualTo(1);

        // and a third termination with an empty queue just releases the slot
        store().decrementAndPop(flow, queuedStore(), (txContext, execution) -> popped.add(execution));
        assertThat(popped).hasSize(2);
        assertThat(currentCount(flow)).isZero();
    }

    @Test
    void shouldNotPopWhenCounterRemainsAtLimit() {
        // Given: the counter was inflated above the limit (over-limit race aftermath), one queued
        Flow flow = flow(1);
        Execution waiting = Execution.newExecution(flow, List.of());
        store().countThenProcess(flow, (txContext, limit) -> {
            queuedStore().save(txContext, queued(flow, waiting, Instant.now()));
            return Pair.of(null, limit.withRunning(3));
        });

        // When: a termination decrements 3 -> 2, still >= limit
        List<Execution> popped = new ArrayList<>();
        store().decrementAndPop(flow, queuedStore(), (txContext, execution) -> popped.add(execution));

        // Then: nothing is dequeued (the queued-protection guard) and the entry survives
        assertThat(popped).isEmpty();
        assertThat(currentCount(flow)).isEqualTo(2);

        // and once enough slots free up, the surviving entry is finally popped
        store().decrementAndPop(flow, queuedStore(), (txContext, execution) -> popped.add(execution));
        store().decrementAndPop(flow, queuedStore(), (txContext, execution) -> popped.add(execution));
        assertThat(popped).extracting(Execution::getId).containsExactly(waiting.getId());
        assertThat(currentCount(flow)).isEqualTo(1);
    }

    private int currentCount(Flow flow) {
        AtomicInteger count = new AtomicInteger();
        store().countThenProcess(flow, (txContext, limit) -> {
            count.set(limit.getRunning() == null ? 0 : limit.getRunning());
            return Pair.of(null, limit);
        });
        return count.get();
    }

    private static ExecutionQueued queued(Flow flow, Execution execution, Instant date) {
        return ExecutionQueued.builder()
            .tenantId(flow.getTenantId())
            .namespace(flow.getNamespace())
            .flowId(flow.getId())
            .execution(execution)
            .date(date)
            .build();
    }
}
