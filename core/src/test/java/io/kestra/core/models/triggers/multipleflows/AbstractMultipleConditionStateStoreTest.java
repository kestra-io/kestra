package io.kestra.core.models.triggers.multipleflows;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

/**
 * Runs the {@link MultipleConditionStateStoreContract} against the container-injected
 * {@link MultipleConditionStateStore} — extended by the JDBC (H2/Postgres/MySQL) and EE
 * Elasticsearch backends. The scenarios themselves live in the annotation-free contract
 * superclass so the executor testkit's in-memory fake is held to the same contract without
 * booting Micronaut.
 */
@MicronautTest(transactional = false)
public abstract class AbstractMultipleConditionStateStoreTest extends MultipleConditionStateStoreContract {

    @Inject
    private MultipleConditionStateStore multipleConditionStateStore;

    @Override
    protected MultipleConditionStateStore store() {
        return multipleConditionStateStore;
    }
}
