package io.kestra.executor.testkit;

import io.kestra.core.models.triggers.multipleflows.MultipleConditionStateStore;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionStateStoreContract;

/**
 * Holds {@link InMemoryMultipleConditionStateStore} to the same
 * {@link MultipleConditionStateStoreContract} as the JDBC and Elasticsearch backends — the fake
 * stays provably faithful to production. Plain JUnit, no Micronaut.
 */
class InMemoryMultipleConditionStateStoreTest extends MultipleConditionStateStoreContract {

    private final InMemoryMultipleConditionStateStore store = new InMemoryMultipleConditionStateStore();

    @Override
    protected MultipleConditionStateStore store() {
        return store;
    }
}
