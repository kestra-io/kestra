package io.kestra.runner.postgres;

import io.kestra.executor.AbstractExecutionDelayStateStoreTest;
import io.kestra.executor.ExecutionDelayStateStore;

import jakarta.inject.Inject;

class PostgresExecutionDelayStateStoreContractTest extends AbstractExecutionDelayStateStoreTest {

    @Inject
    PostgresExecutionDelayStateStoreContractTest(ExecutionDelayStateStore executionDelayStateStore) {
        super(executionDelayStateStore);
    }
}
