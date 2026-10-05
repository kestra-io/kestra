package io.kestra.executor;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

@MicronautTest(transactional = false)
public abstract class AbstractExecutionDelayStateStoreTest extends ExecutionDelayStateStoreContract {

    @Inject
    protected ExecutionDelayStateStore executionDelayStateStore;

    @Override
    protected ExecutionDelayStateStore store() {
        return executionDelayStateStore;
    }
}
