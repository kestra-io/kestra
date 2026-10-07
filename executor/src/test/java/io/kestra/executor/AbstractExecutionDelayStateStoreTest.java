package io.kestra.executor;

import java.util.Objects;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

@MicronautTest(transactional = false)
public abstract class AbstractExecutionDelayStateStoreTest extends ExecutionDelayStateStoreContract {

    private final ExecutionDelayStateStore executionDelayStateStore;

    @Inject
    public AbstractExecutionDelayStateStoreTest(ExecutionDelayStateStore executionDelayStateStore) {
        this.executionDelayStateStore = Objects.requireNonNull(executionDelayStateStore);
    }

    @Override
    protected ExecutionDelayStateStore store() {
        return executionDelayStateStore;
    }
}