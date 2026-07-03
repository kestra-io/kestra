package io.kestra.repository.postgres.migration;

import javax.sql.DataSource;

import io.kestra.core.migration.MigrationScript;
import io.kestra.jdbc.migration.AbstractSQLMigrationScript;
import io.kestra.repository.postgres.PostgresRepositoryEnabled;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * OSS PostgreSQL migration: drops the redundant {@code logs_execution_id} and
 * {@code logs_timestamp} indexes from the {@code logs} table and adds a composite
 * {@code logs_tenant_namespace_flow_id_timestamp} index to fix full-scan on flow-scoped log
 * queries.
 */
@Singleton
@PostgresRepositoryEnabled
public class V2_0_11LogsFlowIndexMigration extends AbstractSQLMigrationScript {

    private static final String SCRIPT_ID = "2.0.11-logs-flow-index";
    private static final String RESOURCE = "/migrations/2.0.11-logs-flow-index-postgres.sql";

    private final DataSource dataSource;

    @Inject
    public V2_0_11LogsFlowIndexMigration(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String scriptId() {
        return SCRIPT_ID;
    }

    @Override
    public String description() {
        return "OSS PostgreSQL: optimise logs table indexes — drop redundant logs_execution_id and logs_timestamp, add logs_tenant_namespace_flow_id_timestamp";
    }

    @Override
    public String checksum() {
        return MigrationScript.checksumOfResources(RESOURCE);
    }

    @Override
    public void migrate() throws Exception {
        executeSqlResource(dataSource, RESOURCE);
    }
}
