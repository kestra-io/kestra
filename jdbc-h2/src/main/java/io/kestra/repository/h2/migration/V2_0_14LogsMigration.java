package io.kestra.repository.h2.migration;

import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import io.kestra.jdbc.LogJdbcDataSourceProvider;
import io.kestra.jdbc.migration.AbstractSQLMigrationScript;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

/**
 * H2 log-store init migration.
 *
 * <p>
 * Creates the log table (idempotently) in the log datasource — the dedicated database when
 * {@code kestra.logs.h2.url} is set, otherwise the primary datasource. Active only when
 * {@code kestra.logs.type} is h2/memory, so a pure back-compat install (no {@code kestra.logs.type})
 * runs nothing and keeps using the baseline-created {@code logs} table.
 */
@Singleton
@Requires(property = "kestra.logs.type", pattern = "h2|memory")
public class V2_0_14LogsMigration extends AbstractSQLMigrationScript {

    private static final String SCRIPT_ID = "2.0.14-logs-h2";
    private static final String SQL_RESOURCE = "/migrations/logs-h2.sql";

    private final LogJdbcDataSourceProvider logDataSourceProvider;

    public V2_0_14LogsMigration(final LogJdbcDataSourceProvider logDataSourceProvider) {
        this.logDataSourceProvider = logDataSourceProvider;
    }

    @Override
    public String scriptId() {
        // The scriptId encodes the backend ("-h2") and a non-default table name, so changing
        // kestra.logs.h2.table auto-applies a fresh migration rather than being skipped as "already
        // applied". The default 'logs' keeps the bare id so existing installs' history is unchanged.
        String table = logDataSourceProvider.table();
        return "logs".equals(table) ? SCRIPT_ID : SCRIPT_ID + "-" + table;
    }

    @Override
    public String description() {
        return "H2 log store init: create the log table";
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
        // Custom migrate() (vs the inherited default): substitute the configurable ${table} name into
        // the DDL before executing. checksum() is inherited (derived from sqlResources()).
        executeSqlScript(dataSource(), SQL_RESOURCE, Map.of("table", logDataSourceProvider.table()));
    }
}
