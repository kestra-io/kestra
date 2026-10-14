package io.kestra.executor;

import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.runners.ExecutionQueuedStateStore;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

/**
 * Runs the {@link ScopedConcurrencyLimitStateStoreContract} against the container-injected
 * {@link ConcurrencyLimitStateStore} — extended by the EE JDBC and Elasticsearch backends. The
 * scenarios live in the annotation-free contract superclass so the executor testkit's in-memory
 * fake is held to the same scoped semantics without booting Micronaut.
 */
@MicronautTest(transactional = false)
public abstract class AbstractScopedConcurrencyLimitStateStoreTest extends ScopedConcurrencyLimitStateStoreContract {

    @Inject
    protected ConcurrencyLimitStateStore store;

    @Inject
    protected ExecutionQueuedStateStore queuedStore;

    @Inject
    protected MetricRegistry metricRegistry;

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
