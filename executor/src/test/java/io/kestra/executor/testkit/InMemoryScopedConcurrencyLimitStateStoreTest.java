package io.kestra.executor.testkit;

import java.util.Map;

import io.kestra.core.metrics.MetricConfig;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.runners.ExecutionQueuedStateStore;
import io.kestra.executor.ConcurrencyLimitStateStore;
import io.kestra.executor.ScopedConcurrencyLimitStateStoreContract;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Proves the testkit's {@link InMemoryConcurrencyLimitStateStore} honors the same multi-scope
 * semantics as the production EE stores — widest-scope pop, blocked-candidate skip, scope isolation.
 */
class InMemoryScopedConcurrencyLimitStateStoreTest extends ScopedConcurrencyLimitStateStoreContract {
    private final InMemoryConcurrencyLimitStateStore store = new InMemoryConcurrencyLimitStateStore();
    private final InMemoryExecutionQueuedStateStore queuedStore = new InMemoryExecutionQueuedStateStore();
    private final MetricRegistry metricRegistry = new MetricRegistry(new SimpleMeterRegistry(), new MetricConfig(null, null, null, Map.of()));

    @Override
    protected ConcurrencyLimitStateStore store() {
        return store;
    }

    @Override
    protected ExecutionQueuedStateStore queuedStore() {
        return queuedStore;
    }

    @Override
    protected MetricRegistry metricRegistry() {
        return metricRegistry;
    }
}
