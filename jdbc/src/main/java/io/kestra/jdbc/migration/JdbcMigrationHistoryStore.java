package io.kestra.jdbc.migration;

import io.kestra.core.migration.MigrationHistoryStore;
import io.kestra.core.migration.MigrationScript;
import io.kestra.jdbc.runner.JdbcRepositoryEnabled;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.jdbc.DataSourceResolver;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;

/**
 * JDBC implementation of {@link MigrationHistoryStore}.
 *
 * <p>Tracks applied migration scripts in the {@code kestra_migration_history} SQL table.
 * Only active when the repository backend is JDBC-based (H2, MySQL, PostgreSQL).
 * Uses {@code @JdbcRepositoryEnabled} rather than {@code @Requires(beans = DataSource.class)}
 * to avoid activating when only a queue DataSource exists (e.g. ELS repository + H2 queue).
 */
@Slf4j
@Singleton
@JdbcRepositoryEnabled
public class JdbcMigrationHistoryStore implements MigrationHistoryStore {

    static final String HISTORY_TABLE = "kestra_migration_history";

    protected final DataSource dataSource;

    @Inject
    public JdbcMigrationHistoryStore(final DataSource dataSource,
                                     @Nullable final DataSourceResolver dataSourceResolver) {
        // Unwrap any Micronaut Data AOP proxy (ContextualConnectionInterceptor) so that
        // direct getConnection() calls work without requiring a @Connectable context.
        this.dataSource = dataSourceResolver != null ? dataSourceResolver.resolve(dataSource) : dataSource;
    }

    /**
     * Constructor for testing with a raw (non-proxied) DataSource, bypassing the resolver.
     */
    public JdbcMigrationHistoryStore(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void bootstrapIfNeeded() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement stmt = connection.createStatement()) {
            prepareDatabase(connection, stmt);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS %s (
                    script_id    VARCHAR(255) NOT NULL,
                    description  VARCHAR(500) NOT NULL,
                    checksum     VARCHAR(64),
                    installed_on TIMESTAMP    NOT NULL,
                    execution_ms BIGINT       NOT NULL,
                    success      BOOLEAN      NOT NULL,
                    CONSTRAINT pk_%s PRIMARY KEY (script_id)
                )
                """.formatted(HISTORY_TABLE, HISTORY_TABLE));
        }
    }

    /**
     * Called before the history table is created. Subclasses can override to prepare
     * database structures (e.g. create schemas).
     */
    protected void prepareDatabase(final Connection connection, final Statement stmt) throws SQLException {
        // no-op by default
    }

    @Override
    public boolean hasAnyApplied() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + HISTORY_TABLE)) {
            rs.next();
            return rs.getLong(1) > 0;
        }
    }

    @Override
    public boolean isApplied(final String scriptId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                 "SELECT 1 FROM " + HISTORY_TABLE + " WHERE script_id = ? AND success = TRUE")) {
            ps.setString(1, scriptId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    @Override
    public void validateChecksum(final MigrationScript script) throws SQLException {
        if (script.checksum() == null) {
            return;
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                 "SELECT checksum FROM " + HISTORY_TABLE + " WHERE script_id = ?")) {
            ps.setString(1, script.scriptId());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String stored = rs.getString("checksum");
                    if (!script.checksum().equals(stored)) {
                        throw new IllegalStateException(
                            ("Checksum mismatch for migration [%s]: stored=%s, current=%s. "
                            + "Do not modify migration scripts after they have been applied.")
                                .formatted(script.scriptId(), stored, script.checksum())
                        );
                    }
                }
            }
        }
    }

    @Override
    public void markApplied(final MigrationScript script, final long executionMs) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                 "INSERT INTO " + HISTORY_TABLE
                 + " (script_id, description, checksum, installed_on, execution_ms, success)"
                 + " VALUES (?, ?, ?, ?, ?, TRUE)")) {
            ps.setString(1, script.scriptId());
            ps.setString(2, script.description());
            ps.setString(3, script.checksum());
            ps.setTimestamp(4, Timestamp.from(Instant.now()));
            ps.setLong(5, executionMs);
            ps.executeUpdate();
        }
    }

    @Override
    public boolean detectLegacyUpgrade() throws SQLException {
        if (hasAnyApplied()) {
            return false;
        }
        // Use case-insensitive comparison to support H2 (uppercases names), PostgreSQL (lowercase),
        // and MySQL (platform-dependent case sensitivity).
        try (Connection connection = dataSource.getConnection()) {
            String historyTable = null;
            DatabaseMetaData meta = connection.getMetaData();
            try (ResultSet rs = meta.getTables(null, null, null, new String[]{"TABLE"})) {
                while (rs.next()) {
                    String name = rs.getString("TABLE_NAME");
                    if ("flyway_schema_history".equalsIgnoreCase(name)) {
                        historyTable = name;
                        break;
                    }
                }
            }
            if (historyTable == null) {
                return false;
            }
            // Quote the identifier so the exact case stored by Flyway is preserved:
            // H2 folds unquoted names to uppercase, so an unquoted SELECT against the
            // Flyway-created lowercase "flyway_schema_history" would fail.
            String quote = meta.getIdentifierQuoteString();
            if (quote == null || quote.isBlank()) {
                quote = "";
            }
            // Micronaut-Flyway auto-creates an empty flyway_schema_history even when no migrations
            // are applied; treat it as a Flyway upgrade only if it actually has prior entries.
            try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM " + quote + historyTable + quote);
                 ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }
}
