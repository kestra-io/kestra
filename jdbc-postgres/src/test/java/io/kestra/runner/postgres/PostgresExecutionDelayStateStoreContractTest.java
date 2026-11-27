package io.kestra.runner.postgres;

import io.kestra.executor.ExecutionDelayStateStore;
import io.kestra.executor.ExecutionDelayStateStoreContract;

import jakarta.inject.Inject;

class PostgresExecutionDelayStateStoreContractTest extends ExecutionDelayStateStoreContract {

    @Inject
    PostgresExecutionDelayStateStoreContractTest(ExecutionDelayStateStore executionDelayStateStore) {
        super(executionDelayStateStore);
    }
}
