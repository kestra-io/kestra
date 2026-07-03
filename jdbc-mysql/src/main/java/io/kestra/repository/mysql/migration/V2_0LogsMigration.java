package io.kestra.repository.mysql.migration;

import java.util.Map;

import io.kestra.core.migration.MigrationScript;
import io.kestra.jdbc.LogJdbcDataSourceProvider;
import io.kestra.jdbc.migration.AbstractSQLMigrationScript;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

/**
 * MySQL log-store init migration.
 *
 * <p>
 * Creates the log table (idempotently) in the log datasource — the dedicated database when
 * {@code kestra.logs.mysql.url} is set, otherwise the primary datasource. Active only when
 * {@code kestra.logs.type} is mysql, so a pure back-compat install runs nothing and keeps using the
 * baseline-created {@code logs} table.
 */
@Singleton
@Requires(property = "kestra.logs.type", value = "mysql")
public class V2_0LogsMigration extends AbstractSQLMigrationScript {

    // The scriptId encodes the backend ("-mysql"): the log store is pluggable/switchable and scriptId
    // is the immutable migration-history PK, so a per-backend id runs the new backend's init on a
    // switch while a switch-back stays a no-op. See H2's V2_0LogsMigration for the full rationale.
    private static final String SCRIPT_ID = "0-init-logs-mysql";

    private final LogJdbcDataSourceProvider logDataSourceProvider;

    public V2_0LogsMigration(final LogJdbcDataSourceProvider logDataSourceProvider) {
        this.logDataSourceProvider = logDataSourceProvider;
    }

    @Override
    public String scriptId() {
        // Non-default table name → its own scriptId, so changing kestra.logs.mysql.table auto-applies
        // a fresh migration. Default 'logs' keeps the bare id. See H2 V2_0LogsMigration for the rationale.
        String table = logDataSourceProvider.table();
        return "logs".equals(table) ? SCRIPT_ID : SCRIPT_ID + "-" + table;
    }

    @Override
    public String description() {
        return "MySQL log store init: create the log table";
    }

    @Override
    public String checksum() {
        return MigrationScript.checksumOfResources("/migrations/logs-mysql.sql");
    }

    @Override
    public void migrate() throws Exception {
        executeSqlScript(
            logDataSourceProvider.dataSource(),
            "/migrations/logs-mysql.sql",
            Map.of("table", logDataSourceProvider.table())
        );
    }
}
