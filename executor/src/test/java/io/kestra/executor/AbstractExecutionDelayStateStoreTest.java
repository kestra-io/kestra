package io.kestra.executor;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

/**
 * Runs the {@link ExecutionDelayStateStoreContract} against the container-injected
 * {@link ExecutionDelayStateStore} — extended by the JDBC (H2/Postgres/MySQL) backends. The scenarios
 * themselves live in the annotation-free contract superclass so the executor testkit's in-memory fake
 * can be held to the same contract without booting Micronaut.
 */
@MicronautTest(transactional = false)
public abstract class AbstractExecutionDelayStateStoreTest extends ExecutionDelayStateStoreContract {

    @Inject
    protected ExecutionDelayStateStore executionDelayStateStore;

    @Override
    protected ExecutionDelayStateStore store() {
        return executionDelayStateStore;
    }
}
