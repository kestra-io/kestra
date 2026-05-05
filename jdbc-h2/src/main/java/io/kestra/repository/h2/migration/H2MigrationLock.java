package io.kestra.repository.h2.migration;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import io.kestra.core.migration.MigrationLock;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * In-memory {@link MigrationLock} for H2 using a {@link ReentrantLock}.
 *
 * <p>
 * H2 is an embedded, single-process database, so distributed locking is not needed.
 * A JVM-level lock is still used to guard against concurrent threads (e.g. in tests).
 * <p>
 * Active only when H2 is the <em>repository</em> backend, not just the queue, to avoid
 * conflicting with the Elasticsearch repository backend when H2 is used only as the queue.
 */
@Slf4j
@Singleton
@Requires(property = "kestra.repository.type", pattern = "h2|memory")
public class H2MigrationLock implements MigrationLock {

    private final ReentrantLock lock = new ReentrantLock();
    private final Duration lockTimeout;

    @Inject
    public H2MigrationLock(
        @Property(name = "kestra.migration.lock-acquire-timeout", defaultValue = "PT1H") final Duration lockTimeout) {
        this.lockTimeout = lockTimeout;
    }

    @Override
    public void acquire() throws Exception {
        if (!lock.tryLock(lockTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
            throw new IllegalStateException(
                "Could not acquire H2 migration lock within " + lockTimeout + " (configurable via kestra.migration.lock-acquire-timeout)"
            );
        }
    }

    @Override
    public boolean tryAcquire() {
        return lock.tryLock();
    }

    @Override
    public void forceRelease() {
        log.warn(
            "H2 uses a JVM-level lock that cannot be released from another process. "
                + "Restart the application to release the lock."
        );
    }

    @Override
    public void release() {
        lock.unlock();
    }
}
