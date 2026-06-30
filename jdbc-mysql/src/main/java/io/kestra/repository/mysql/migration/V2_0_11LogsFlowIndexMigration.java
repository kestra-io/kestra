package io.kestra.repository.mysql.migration;

import javax.sql.DataSource;

import io.kestra.core.migration.MigrationScript;
import io.kestra.jdbc.migration.AbstractSQLMigrationScript;
import io.kestra.repository.mysql.MysqlRepositoryEnabled;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * OSS MySQL migration: drops the redundant {@code ix_execution_id} and {@code ix_timestamp}
 * indexes from the {@code logs} table and adds a composite
 * {@code ix_tenant_namespace_flow_id_timestamp} index to fix full-scan on flow-scoped log queries.
 */
@Singleton
@MysqlRepositoryEnabled
public class V2_0_11LogsFlowIndexMigration extends AbstractSQLMigrationScript {

    private static final String SCRIPT_ID = "2.0.11-logs-flow-index";
    private static final String RESOURCE = "/migrations/2.0.11-logs-flow-index-mysql.sql";

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
        return "OSS MySQL: optimise logs table indexes — drop redundant ix_execution_id and ix_timestamp, add ix_tenant_namespace_flow_id_timestamp";
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
