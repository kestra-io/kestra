package io.kestra.runner.postgres;

import java.util.TimeZone;

import io.kestra.executor.AbstractExecutionDelayStateStoreTest;
import io.kestra.executor.ExecutionDelayStateStore;

import jakarta.inject.Inject;

/**
 * Runs the delay contract under a non-UTC JVM default zone so that a future Instant conversion to
 * a naive LocalDateTime would be observed instead of being masked by UTC CI.
 */
class PostgresExecutionDelayStateStoreContractTest extends AbstractExecutionDelayStateStoreTest {

    @Inject
    PostgresExecutionDelayStateStoreContractTest(ExecutionDelayStateStore executionDelayStateStore) {
        super(executionDelayStateStore);
    }

    @Override
    protected TimeZone testTimeZone() {
        return TimeZone.getTimeZone("Asia/Kolkata");
    }
}
