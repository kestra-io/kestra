package io.kestra.jdbc.migration;

import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import io.kestra.jdbc.LogJdbcDataSourceProvider;

/**
 * Abstract base for the log-store {@code task_id}/{@code trigger_id} widening migration, shared
 * by every JDBC dialect. Widens both generated columns to {@code VARCHAR(256)} on the log-store
 * table resolved by {@link LogJdbcDataSourceProvider} (a dedicated database when a log-specific
 * URL is configured, otherwise the primary datasource).
 *
 * <p>
 * Mirrors {@code V2_0_05LogsMigration}: the scriptId encodes the backend and a non-default table
 * name, so switching the log-repo backend or table re-applies it.
 */
public abstract class AbstractV2_0_06WidenLogsMigration extends AbstractSQLMigrationScript {

    private final String dialect;
    private final String sqlResource;
    private final LogJdbcDataSourceProvider logDataSourceProvider;

    protected AbstractV2_0_06WidenLogsMigration(
        final String dialect,
        final String sqlResource,
        final LogJdbcDataSourceProvider logDataSourceProvider) {
        this.dialect = dialect;
        this.sqlResource = sqlResource;
        this.logDataSourceProvider = logDataSourceProvider;
    }

    @Override
    public String scriptId() {
        String base = "2.0.06-widen-logs-" + dialect;
        String table = logDataSourceProvider.table();
        return "logs".equals(table) ? base : base + "-" + table;
    }

    @Override
    public String description() {
        return "Dedicated log store: widen task_id and trigger_id to VARCHAR(256)";
    }

    @Override
    public List<String> sqlResources() {
        return List.of(sqlResource);
    }

    @Override
    protected DataSource dataSource() {
        return logDataSourceProvider.dataSource();
    }

    @Override
    public void migrate() throws Exception {
        executeSqlScript(dataSource(), sqlResource, Map.of("table", logDataSourceProvider.table()));
    }
}
