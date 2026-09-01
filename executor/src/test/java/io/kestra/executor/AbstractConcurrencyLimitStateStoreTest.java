package io.kestra.executor;

import io.kestra.core.runners.ExecutionQueuedStateStore;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

/**
 * Runs the {@link ConcurrencyLimitStateStoreContract} against the container-injected
 * {@link ConcurrencyLimitStateStore} — extended by the JDBC (H2/Postgres/MySQL) backends.
 * The scenarios themselves live in the annotation-free contract superclass so the executor
 * testkit's in-memory fake is held to the same contract without booting Micronaut.
 */
@MicronautTest(transactional = false)
public abstract class AbstractConcurrencyLimitStateStoreTest extends ConcurrencyLimitStateStoreContract {

    @Inject
    protected ConcurrencyLimitStateStore concurrencyLimitStateStore;

    @Inject
    protected ExecutionQueuedStateStore executionQueuedStateStore;

    @Override
    protected ConcurrencyLimitStateStore store() {
        return concurrencyLimitStateStore;
    }

    @Override
    protected ExecutionQueuedStateStore queuedStore() {
        return executionQueuedStateStore;
    }
}
