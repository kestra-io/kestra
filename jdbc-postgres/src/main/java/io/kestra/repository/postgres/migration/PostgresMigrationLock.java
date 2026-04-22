package io.kestra.repository.postgres.migration;

import io.kestra.core.migration.MigrationLock;
import io.kestra.repository.postgres.PostgresRepositoryEnabled;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.jdbc.DataSourceResolver;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * PostgreSQL {@link MigrationLock} implementation using {@code pg_advisory_lock} /
 * {@code pg_advisory_unlock}.
 *
 * <p>Advisory locks are session-scoped in PostgreSQL: they persist across transactions and
 * are automatically released when the connection is closed.  This makes them safe for
 * multi-node deployments where multiple instances may start concurrently.
 */
@Slf4j
@Singleton
@PostgresRepositoryEnabled
public class PostgresMigrationLock implements MigrationLock {

    /** Arbitrary constant used as the advisory lock key. */
    private static final long LOCK_KEY = 7_516_827L;

    private final DataSource dataSource;

    /** Dedicated connection held open for the duration of the lock. {@code volatile} because
     * {@link #acquire()} and {@link #release()} can be called from different JVM instances or
     * threads in a multi-node deployment. Without visibility, {@link #release()} could observe
     * {@code null} and silently skip the advisory-lock release, leaving other nodes blocked. */
    private volatile Connection lockConnection;

    @Inject
    public PostgresMigrationLock(final DataSource dataSource,
                                 @Nullable final DataSourceResolver dataSourceResolver) {
        this.dataSource = dataSourceResolver != null ? dataSourceResolver.resolve(dataSource) : dataSource;
    }

    @Override
    public void acquire() throws SQLException {
        log.debug("Acquiring PostgreSQL advisory migration lock (key={})", LOCK_KEY);
        lockConnection = dataSource.getConnection();
        try (Statement stmt = lockConnection.createStatement()) {
            stmt.execute("SELECT pg_advisory_lock(" + LOCK_KEY + ")");
        } catch (SQLException e) {
            lockConnection.close();
            lockConnection = null;
            throw e;
        }
        log.debug("PostgreSQL advisory migration lock acquired (key={})", LOCK_KEY);
    }

    @Override
    public boolean tryAcquire() throws SQLException {
        log.debug("Trying to acquire PostgreSQL advisory migration lock (key={}, non-blocking)", LOCK_KEY);
        lockConnection = dataSource.getConnection();
        try (Statement stmt = lockConnection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT pg_try_advisory_lock(" + LOCK_KEY + ")")) {
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
    public void release() throws SQLException {
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
