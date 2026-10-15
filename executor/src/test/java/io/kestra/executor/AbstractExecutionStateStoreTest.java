package io.kestra.executor;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

/**
 * Runs the {@link ExecutionStateStoreContract} against the container-injected
 * {@link ExecutionStateStore} — extended by the JDBC (H2/Postgres/MySQL) and EE Elasticsearch
 * backends. The scenarios themselves live in the annotation-free contract superclass so the
 * executor testkit's in-memory fake is held to the same contract without booting Micronaut.
 */
@MicronautTest(transactional = false)
public abstract class AbstractExecutionStateStoreTest extends ExecutionStateStoreContract {

    @Inject
    protected ExecutionStateStore executionStateStore;

    @Override
    protected ExecutionStateStore store() {
        return executionStateStore;
    }
}
