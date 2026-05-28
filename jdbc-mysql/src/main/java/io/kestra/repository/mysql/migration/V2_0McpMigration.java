package io.kestra.repository.mysql.migration;

import javax.sql.DataSource;

import io.kestra.core.migration.MigrationScript;
import io.kestra.jdbc.migration.AbstractSQLMigrationScript;
import io.kestra.repository.mysql.MysqlRepositoryEnabled;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * OSS MySQL MCP migration script.
 *
 * <p>
 * Creates the {@code mcp} and {@code mcp_session} tables for the MCP server integration feature.
 */
@Singleton
@MysqlRepositoryEnabled
public class V2_0McpMigration extends AbstractSQLMigrationScript {

    private static final String SCRIPT_ID = "2.0-mcp";

    private final DataSource dataSource;

    @Inject
    public V2_0McpMigration(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String scriptId() {
        return SCRIPT_ID;
    }

    @Override
    public String description() {
        return "OSS MySQL MCP: create mcp and mcp_session tables";
    }

    @Override
    public String checksum() {
        return MigrationScript.checksumOfResources("/migrations/upgrade-v2.0-mcp-mysql.sql");
    }

    @Override
    public void migrate() throws Exception {
        executeSqlResource(dataSource, "/migrations/upgrade-v2.0-mcp-mysql.sql");
    }
}
