package io.kestra.repository.mysql.migration;

import io.kestra.core.migration.MigrationLock;
import io.kestra.repository.mysql.MysqlRepositoryEnabled;
import io.micronaut.context.annotation.Property;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.jdbc.DataSourceResolver;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;

/**
 * MySQL {@link MigrationLock} implementation using {@code GET_LOCK} / {@code RELEASE_LOCK}.
 *
 * <p>The lock is session-scoped and persists across transactions, making it safe for
 * multi-node deployments. The acquire timeout is configurable via
 * {@code kestra.migration.lock-acquire-timeout} (default 1 hour).
 */
@Slf4j
@Singleton
@MysqlRepositoryEnabled
public class MysqlMigrationLock implements MigrationLock {

    private static final String LOCK_NAME = "kestra_migration";

    private final DataSource dataSource;
    private final Duration lockTimeout;

    /** Dedicated connection held open for the duration of the lock. {@code volatile} because
     * {@link #acquire()} and {@link #release()} can be called from different JVM instances or
     * threads in a multi-node deployment. Without visibility, {@link #release()} could observe
     * {@code null} and silently skip the GET_LOCK release, leaving other nodes blocked. */
    private volatile Connection lockConnection;

    @Inject
    public MysqlMigrationLock(final DataSource dataSource,
                               @Nullable final DataSourceResolver dataSourceResolver,
                               @Property(name = "kestra.migration.lock-acquire-timeout", defaultValue = "PT1H") final Duration lockTimeout) {
        this.dataSource = dataSourceResolver != null ? dataSourceResolver.resolve(dataSource) : dataSource;
        this.lockTimeout = lockTimeout;
    }

    @Override
    public void acquire() throws SQLException {
        log.debug("Acquiring MySQL migration lock '{}'", LOCK_NAME);
        lockConnection = dataSource.getConnection();
        try (PreparedStatement ps = lockConnection.prepareStatement("SELECT GET_LOCK(?, ?)")) {
            ps.setString(1, LOCK_NAME);
            ps.setInt(2, (int) lockTimeout.toSeconds());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || rs.getInt(1) != 1) {
                    lockConnection.close();
                    lockConnection = null;
                    throw new SQLException(
                        "Could not acquire MySQL migration lock '%s' within %s (configurable via kestra.migration.lock-acquire-timeout)"
                            .formatted(LOCK_NAME, lockTimeout)
                    );
                }
            }
        } catch (SQLException e) {
            if (lockConnection != null) {
                lockConnection.close();
                lockConnection = null;
            }
            throw e;
        }
        log.debug("MySQL migration lock '{}' acquired", LOCK_NAME);
    }

    @Override
    public boolean tryAcquire() throws SQLException {
        log.debug("Trying to acquire MySQL migration lock '{}' (non-blocking)", LOCK_NAME);
        lockConnection = dataSource.getConnection();
        try (PreparedStatement ps = lockConnection.prepareStatement("SELECT GET_LOCK(?, 0)")) {
            ps.setString(1, LOCK_NAME);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getInt(1) == 1) {
                    log.debug("MySQL migration lock '{}' acquired", LOCK_NAME);
                    return true;
                }
                lockConnection.close();
                lockConnection = null;
                log.debug("MySQL migration lock '{}' is held by another process", LOCK_NAME);
                return false;
            }
        } catch (SQLException e) {
            if (lockConnection != null) {
                lockConnection.close();
                lockConnection = null;
            }
            throw e;
        }
    }

    @Override
    public void release() throws SQLException {
        if (lockConnection == null) {
            return;
        }
        try {
            try (PreparedStatement ps = lockConnection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
                ps.setString(1, LOCK_NAME);
                ps.executeQuery();
            }
            log.debug("MySQL migration lock '{}' released", LOCK_NAME);
        } finally {
            lockConnection.close();
            lockConnection = null;
        }
    }
}
