package io.kestra.repository.mysql.migration;

import io.kestra.core.migration.MigrationLock;
import io.kestra.repository.mysql.MysqlRepositoryEnabled;
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

/**
 * MySQL {@link MigrationLock} implementation using {@code GET_LOCK} / {@code RELEASE_LOCK}.
 *
 * <p>The lock is session-scoped and persists across transactions, making it safe for
 * multi-node deployments. The lock times out after 5 minutes if not acquired.
 */
@Slf4j
@Singleton
@MysqlRepositoryEnabled
public class MysqlMigrationLock implements MigrationLock {

    private static final String LOCK_NAME = "kestra_migration";
    private static final int LOCK_TIMEOUT_SECONDS = 300;

    private final DataSource dataSource;

    /** Dedicated connection held open for the duration of the lock. {@code volatile} because
     * {@link #acquire()} and {@link #release()} can be called from different JVM instances or
     * threads in a multi-node deployment. Without visibility, {@link #release()} could observe
     * {@code null} and silently skip the GET_LOCK release, leaving other nodes blocked. */
    private volatile Connection lockConnection;

    @Inject
    public MysqlMigrationLock(final DataSource dataSource,
                               @Nullable final DataSourceResolver dataSourceResolver) {
        this.dataSource = dataSourceResolver != null ? dataSourceResolver.resolve(dataSource) : dataSource;
    }

    @Override
    public void acquire() throws SQLException {
        log.debug("Acquiring MySQL migration lock '{}'", LOCK_NAME);
        lockConnection = dataSource.getConnection();
        try (PreparedStatement ps = lockConnection.prepareStatement("SELECT GET_LOCK(?, ?)")) {
            ps.setString(1, LOCK_NAME);
            ps.setInt(2, LOCK_TIMEOUT_SECONDS);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || rs.getInt(1) != 1) {
                    lockConnection.close();
                    lockConnection = null;
                    throw new SQLException(
                        "Could not acquire MySQL migration lock '%s' within %d seconds"
                            .formatted(LOCK_NAME, LOCK_TIMEOUT_SECONDS)
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
