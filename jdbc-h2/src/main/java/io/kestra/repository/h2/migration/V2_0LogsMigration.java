package io.kestra.repository.h2.migration;

import java.util.Map;

import io.kestra.core.migration.MigrationScript;
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
 * runs nothing and keeps using the baseline-created {@code logs} table. Tracked in the main
 * repository's migration history via a type-encoded, immutable {@code scriptId}.
 */
@Singleton
@Requires(property = "kestra.logs.type", pattern = "h2|memory")
public class V2_0LogsMigration extends AbstractSQLMigrationScript {

    // Unlike our other migrations, the scriptId encodes the backend ("-h2"). The log store is
    // pluggable and can change over an install's lifetime (kestra.logs.type). Because scriptId is the
    // immutable primary key in the migration history, a per-backend id means switching backends (e.g.
    // h2 -> postgres) runs the new backend's never-applied init script, while switching back is a
    // no-op (its id is already recorded, and the DDL is idempotent anyway). A generic "0-init-logs"
    // would be marked applied by the first backend and never run again for the others.
    private static final String SCRIPT_ID = "0-init-logs-h2";

    private final LogJdbcDataSourceProvider logDataSourceProvider;

    public V2_0LogsMigration(final LogJdbcDataSourceProvider logDataSourceProvider) {
        this.logDataSourceProvider = logDataSourceProvider;
    }

    @Override
    public String scriptId() {
        // Encode a non-default table name so that changing kestra.logs.h2.table auto-applies a fresh
        // migration (its own scriptId) rather than being skipped as "already applied". The default
        // 'logs' keeps the bare id so existing installs' migration history is unchanged.
        String table = logDataSourceProvider.table();
        return "logs".equals(table) ? SCRIPT_ID : SCRIPT_ID + "-" + table;
    }

    @Override
    public String description() {
        return "H2 log store init: create the log table";
    }

    @Override
    public String checksum() {
        return MigrationScript.checksumOfResources("/migrations/logs-h2.sql");
    }

    @Override
    public void migrate() throws Exception {
        executeSqlScript(
            logDataSourceProvider.dataSource(),
            "/migrations/logs-h2.sql",
            Map.of("table", logDataSourceProvider.table())
        );
    }
}
