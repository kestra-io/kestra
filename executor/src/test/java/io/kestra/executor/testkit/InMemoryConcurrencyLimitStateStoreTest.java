package io.kestra.executor.testkit;

import io.kestra.core.runners.ExecutionQueuedStateStore;
import io.kestra.executor.ConcurrencyLimitStateStore;
import io.kestra.executor.ConcurrencyLimitStateStoreContract;

/**
 * Proves the testkit's {@link InMemoryConcurrencyLimitStateStore} honors the same behavioral
 * contract as the production JDBC implementations — plain JUnit, no Micronaut, no database.
 */
class InMemoryConcurrencyLimitStateStoreTest extends ConcurrencyLimitStateStoreContract {

    private final InMemoryConcurrencyLimitStateStore store = new InMemoryConcurrencyLimitStateStore();
    private final InMemoryExecutionQueuedStateStore queuedStore = new InMemoryExecutionQueuedStateStore();

    @Override
    protected ConcurrencyLimitStateStore store() {
        return store;
    }

    @Override
    protected ExecutionQueuedStateStore queuedStore() {
        return queuedStore;
    }
}
