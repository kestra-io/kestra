package io.kestra.executor;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

/**
 * Records the messages the executor emitted, keyed by type, so a test can assert on them and the
 * harness can drain the executor's own follow-up events. One shared instance the fake queues write
 * to; the harness resets it at the start of each {@code process}, so it reflects the last cycle.
 *
 * <p>Shared because the fake queues are context singletons wired into the singleton executor. This
 * makes the harness serial (the default); running these tests in parallel would need a per-test
 * recorder reached through a thread-scoped indirection.
 */
@Singleton
@Requires(property = "kestra.test.executor-state-machine-harness", value = "true")
public class QueueRecorder {
    private final Map<Class<?>, List<Object>> byType = new ConcurrentHashMap<>();

    public <T> void record(Class<T> type, T message) {
        byType.computeIfAbsent(type, ignored -> new CopyOnWriteArrayList<>()).add(message);
    }

    @SuppressWarnings("unchecked")
    public <T> List<T> emitted(Class<T> type) {
        return List.copyOf((List<T>) byType.getOrDefault(type, List.of()));
    }

    public void reset() {
        byType.values().forEach(List::clear);
    }
}
