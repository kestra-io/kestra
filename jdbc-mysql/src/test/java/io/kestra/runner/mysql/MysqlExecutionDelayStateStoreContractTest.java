package io.kestra.runner.mysql;

import io.kestra.executor.ExecutionDelayStateStore;
import io.kestra.executor.ExecutionDelayStateStoreContract;

import jakarta.inject.Inject;

class MysqlExecutionDelayStateStoreContractTest extends ExecutionDelayStateStoreContract {

    @Inject
    MysqlExecutionDelayStateStoreContractTest(ExecutionDelayStateStore executionDelayStateStore) {
        super(executionDelayStateStore);
    }
}
