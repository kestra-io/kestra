package io.kestra.repository.h2.migration;

import javax.sql.DataSource;

import io.kestra.core.migration.MigrationScript;
import io.kestra.jdbc.migration.AbstractSQLMigrationScript;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * OSS H2 migration: drops the redundant {@code logs_execution_id} and {@code logs_timestamp}
 * indexes from the {@code logs} table and adds a composite
 * {@code logs_tenant_namespace_flow_id_timestamp} index to fix full-scan on flow-scoped log
 * queries.
 */
@Singleton
@Requires(property = "kestra.repository.type", pattern = "h2|memory")
public class V2_0_11LogsFlowIndexMigration extends AbstractSQLMigrationScript {

    private static final String SCRIPT_ID = "2.0.11-logs-flow-index";
    private static final String RESOURCE = "/migrations/2.0.11-logs-flow-index-h2.sql";

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
        return "OSS H2: optimise logs table indexes — drop redundant logs_execution_id and logs_timestamp, add logs_tenant_namespace_flow_id_timestamp";
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
