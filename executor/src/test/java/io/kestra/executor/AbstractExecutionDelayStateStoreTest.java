package io.kestra.executor;

import java.util.Objects;
import java.util.TimeZone;
import java.util.concurrent.locks.ReentrantLock;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

@MicronautTest(transactional = false)
public abstract class AbstractExecutionDelayStateStoreTest extends ExecutionDelayStateStoreContract {

    // TimeZone.setDefault is process-wide, so the zone is taken and given back under one lock.
    private static final ReentrantLock ZONE_LOCK = new ReentrantLock();

    private final ExecutionDelayStateStore executionDelayStateStore;

    private TimeZone originalTimeZone;

    @Inject
    public AbstractExecutionDelayStateStoreTest(ExecutionDelayStateStore executionDelayStateStore) {
        this.executionDelayStateStore = Objects.requireNonNull(executionDelayStateStore);
    }

    @Override
    protected ExecutionDelayStateStore store() {
        return executionDelayStateStore;
    }

    /** The JVM zone the contract runs in; jOOQ binds the cutoff with Timestamp.valueOf, which reads it. */
    protected TimeZone testTimeZone() {
        return TimeZone.getTimeZone("UTC");
    }

    @Override
    protected void prepare() {
        ZONE_LOCK.lock();
        originalTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(testTimeZone());
    }

    @Override
    protected void cleanup() {
        try {
            TimeZone.setDefault(originalTimeZone);
        } finally {
            ZONE_LOCK.unlock();
        }
    }
}
