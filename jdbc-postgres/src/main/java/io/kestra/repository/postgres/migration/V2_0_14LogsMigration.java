package io.kestra.repository.postgres.migration;

import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import io.kestra.jdbc.LogJdbcDataSourceProvider;
import io.kestra.jdbc.migration.AbstractSQLMigrationScript;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

/**
 * PostgreSQL log-store init migration.
 *
 * <p>
 * Creates the log table (idempotently) in the log datasource — the dedicated database when
 * {@code kestra.logs.postgres.url} is set, otherwise the primary datasource. Active only when
 * {@code kestra.logs.type} is postgres, so a pure back-compat install keeps using the
 * baseline-created {@code logs} table.
 */
@Singleton
@Requires(property = "kestra.logs.type", value = "postgres")
public class V2_0_14LogsMigration extends AbstractSQLMigrationScript {

    private static final String SCRIPT_ID = "2.0.14-logs-postgres";
    private static final String SQL_RESOURCE = "/migrations/logs-postgres.sql";

    private final LogJdbcDataSourceProvider logDataSourceProvider;

    public V2_0_14LogsMigration(final LogJdbcDataSourceProvider logDataSourceProvider) {
        this.logDataSourceProvider = logDataSourceProvider;
    }

    @Override
    public String scriptId() {
        // Non-default table name → its own scriptId, so changing kestra.logs.postgres.table auto-applies
        // a fresh migration. Default 'logs' keeps the bare id. See H2 V2_0_14LogsMigration for the rationale.
        String table = logDataSourceProvider.table();
        return "logs".equals(table) ? SCRIPT_ID : SCRIPT_ID + "-" + table;
    }

    @Override
    public String description() {
        return "PostgreSQL log store init: create the log table";
    }

    @Override
    public List<String> sqlResources() {
        return List.of(SQL_RESOURCE);
    }

    @Override
    protected DataSource dataSource() {
        return logDataSourceProvider.dataSource();
    }

    @Override
    public void migrate() throws Exception {
        // Custom migrate() (vs the inherited default): substitute the configurable ${table} name.
        executeSqlScript(dataSource(), SQL_RESOURCE, Map.of("table", logDataSourceProvider.table()));
    }
}
