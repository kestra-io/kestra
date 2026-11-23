package io.kestra.executor.testkit;

import io.kestra.executor.ExecutionStateStore;
import io.kestra.executor.ExecutionStateStoreContract;

/**
 * Holds {@link InMemoryExecutionStateStore} to the same {@link ExecutionStateStoreContract} as
 * the JDBC and Elasticsearch backends — the fake stays provably faithful to production.
 * Plain JUnit, no Micronaut.
 */
class InMemoryExecutionStateStoreTest extends ExecutionStateStoreContract {

    private final InMemoryExecutionStateStore store = new InMemoryExecutionStateStore();

    @Override
    protected ExecutionStateStore store() {
        return store;
    }
}
