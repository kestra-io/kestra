package io.kestra.repository.postgres.migration;

import io.kestra.jdbc.migration.JdbcMigrationHistoryStore;
import io.kestra.repository.postgres.PostgresRepositoryEnabled;
import io.micronaut.context.annotation.Replaces;
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
 * PostgreSQL-specific {@link JdbcMigrationHistoryStore} that creates the configured schema
 * before the history table is bootstrapped.
 *
 * <p>When a custom schema is configured via the datasource {@code schema} property (e.g.
 * {@code kestra_unit_webserver}), PostgreSQL's {@link Connection#getSchema()} returns
 * {@code null} because the schema does not exist yet. We query {@code SHOW search_path}
 * directly to obtain the configured (possibly non-existent) schema name and create it
 * if absent. This mirrors Flyway's {@code createSchemas=true} default behaviour.
 */
@Slf4j
@Singleton
@PostgresRepositoryEnabled
@Replaces(JdbcMigrationHistoryStore.class)
public class PostgresMigrationHistoryStore extends JdbcMigrationHistoryStore {

    @Inject
    public PostgresMigrationHistoryStore(final DataSource dataSource,
                                         @Nullable final DataSourceResolver dataSourceResolver) {
        super(dataSource, dataSourceResolver);
    }

    /** Constructor for testing with a raw (non-proxied) DataSource. */
    public PostgresMigrationHistoryStore(final DataSource dataSource) {
        super(dataSource);
    }

    @Override
    protected void prepareDatabase(final Connection connection, final Statement stmt) throws SQLException {
        String schemaToCreate = resolveSchemaFromSearchPath(stmt);
        if (schemaToCreate == null) {
            log.warn("No usable schema found in PostgreSQL search_path. "
                + "The migration history table may fail to create if no default schema is writable.");
            return;
        }
        // Quote the identifier to handle case-sensitive or special-character schema names
        String quoted = schemaToCreate.replace("\"", "\"\"");
        stmt.execute("CREATE SCHEMA IF NOT EXISTS \"" + quoted + "\"");
    }

    /**
     * Parses the PostgreSQL {@code search_path} and returns the first non-system schema name,
     * or {@code null} if none is found.
     */
    static String resolveSchemaFromSearchPath(final Statement stmt) throws SQLException {
        try (ResultSet rs = stmt.executeQuery("SHOW search_path")) {
            if (!rs.next()) {
                return null;
            }
            for (String schema : rs.getString(1).split(",")) {
                schema = schema.trim();
                // Strip surrounding double quotes added by the JDBC driver's setSchema(),
                // and unescape any doubled quotes inside (SQL standard identifier quoting).
                if (schema.startsWith("\"") && schema.endsWith("\"")) {
                    schema = schema.substring(1, schema.length() - 1).replace("\"\"", "\"");
                }
                // Skip PostgreSQL special tokens ($user, pg_catalog, pg_temp, etc.)
                if (!schema.isEmpty() && !schema.startsWith("$") && !schema.startsWith("pg_")) {
                    return schema;
                }
            }
        }
        return null;
    }
}
