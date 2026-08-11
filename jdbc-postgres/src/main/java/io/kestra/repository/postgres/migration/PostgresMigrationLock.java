package io.kestra.repository.postgres.migration;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import io.kestra.core.migration.MigrationLock;
import io.kestra.repository.postgres.PostgresRepositoryEnabled;

import io.micronaut.context.annotation.Property;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.jdbc.DataSourceResolver;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * PostgreSQL {@link MigrationLock} implementation using {@code pg_advisory_lock} /
 * {@code pg_advisory_unlock}.
 *
 * <p>
 * Advisory locks are session-scoped in PostgreSQL: they persist across transactions and
 * are automatically released when the connection is closed. This makes them safe for
 * multi-node deployments where multiple instances may start concurrently.
 */
@Slf4j
@Singleton
@PostgresRepositoryEnabled
public class PostgresMigrationLock implements MigrationLock {

    /** Arbitrary constant used as the advisory lock key. */
    private static final long LOCK_KEY = 7_516_827L;
    private static final long RETRY_DELAY_MS = 1_000;

    private final DataSource dataSource;
    private final Duration lockTimeout;

    /**
     * Dedicated connection held open for the duration of the lock.
     * All access is guarded by {@code synchronized} methods.
     */
    private Connection lockConnection;

    @Inject
    public PostgresMigrationLock(final DataSource dataSource,
        @Nullable final DataSourceResolver dataSourceResolver,
        @Property(name = "kestra.migration.lock-acquire-timeout", defaultValue = "PT1H") final Duration lockTimeout) {
        this.dataSource = dataSourceResolver != null ? dataSourceResolver.resolve(dataSource) : dataSource;
        this.lockTimeout = lockTimeout;
    }

    @Override
    public synchronized void acquire() throws Exception {
        log.debug("Acquiring PostgreSQL advisory migration lock (key={}, timeout={})", LOCK_KEY, lockTimeout);
        long deadline = System.currentTimeMillis() + lockTimeout.toMillis();
        while (true) {
            lockConnection = dataSource.getConnection();
            try (
                Statement stmt = lockConnection.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT pg_try_advisory_lock(" + LOCK_KEY + ")")
            ) {
                if (rs.next() && rs.getBoolean(1)) {
                    log.debug("PostgreSQL advisory migration lock acquired (key={})", LOCK_KEY);
                    return;
                }
                lockConnection.close();
                lockConnection = null;
            } catch (SQLException e) {
                lockConnection.close();
                lockConnection = null;
                throw e;
            }
            if (System.currentTimeMillis() >= deadline) {
                throw new IllegalStateException(
                    "Could not acquire PostgreSQL migration lock within %s (configurable via kestra.migration.lock-acquire-timeout)"
                        .formatted(lockTimeout)
                );
            }
            log.debug("PostgreSQL advisory migration lock held by another process, retrying in {}ms", RETRY_DELAY_MS);
            TimeUnit.MILLISECONDS.sleep(RETRY_DELAY_MS);
        }
    }

    @Override
    public synchronized boolean tryAcquire() throws SQLException {
        log.debug("Trying to acquire PostgreSQL advisory migration lock (key={}, non-blocking)", LOCK_KEY);
        lockConnection = dataSource.getConnection();
        try (
            Statement stmt = lockConnection.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT pg_try_advisory_lock(" + LOCK_KEY + ")")
        ) {
            if (rs.next() && rs.getBoolean(1)) {
                log.debug("PostgreSQL advisory migration lock acquired (key={})", LOCK_KEY);
                return true;
            }
            lockConnection.close();
            lockConnection = null;
            log.debug("PostgreSQL advisory migration lock is held by another process (key={})", LOCK_KEY);
            return false;
        } catch (SQLException e) {
            lockConnection.close();
            lockConnection = null;
            throw e;
        }
    }

    @Override
    public synchronized void forceRelease() {
        log.warn(
            "PostgreSQL advisory locks are session-scoped and cannot be released from another process. "
                + "The lock will be automatically released when the holding process terminates or its connection closes."
        );
    }

    @Override
    public synchronized void release() throws SQLException {
        if (lockConnection == null) {
            return;
        }
        try {
            try (Statement stmt = lockConnection.createStatement()) {
                stmt.execute("SELECT pg_advisory_unlock(" + LOCK_KEY + ")");
            }
            log.debug("PostgreSQL advisory migration lock released (key={})", LOCK_KEY);
        } finally {
            lockConnection.close();
            lockConnection = null;
        }
    }
}
