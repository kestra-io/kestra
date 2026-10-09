package io.kestra.runner.mysql;

import io.kestra.executor.AbstractExecutionDelayStateStoreTest;
import io.kestra.executor.ExecutionDelayStateStore;

import jakarta.inject.Inject;

class MysqlExecutionDelayStateStoreTest extends AbstractExecutionDelayStateStoreTest {

    @Inject
    MysqlExecutionDelayStateStoreTest(ExecutionDelayStateStore executionDelayStateStore) {
        super(executionDelayStateStore);
    }
}
