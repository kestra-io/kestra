package io.kestra.executor.testkit;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import io.kestra.core.runners.ExecutionDelay;
import io.kestra.executor.ExecutionDelayStateStore;

/**
 * List-backed {@link ExecutionDelayStateStore}. {@code processExpired(now, ...)} pops every delay
 * with a date at or before {@code now} — the seam for virtual-time tests.
 */
public class InMemoryExecutionDelayStateStore implements ExecutionDelayStateStore {
    private final List<ExecutionDelay> delays = new CopyOnWriteArrayList<>();

    @Override
    public void processExpired(Instant now, Consumer<ExecutionDelay> consumer) {
        List<ExecutionDelay> expired = delays.stream()
            .filter(delay -> !delay.getDate().isAfter(now))
            .toList();
        expired.forEach(delay ->
        {
            consumer.accept(delay);
            delays.remove(delay);
        });
    }

    @Override
    public void save(ExecutionDelay executionDelay) {
        delays.add(executionDelay);
    }

    /**
     * Every pending (not yet expired-and-consumed) delay.
     */
    public List<ExecutionDelay> pending() {
        return List.copyOf(delays);
    }
}
