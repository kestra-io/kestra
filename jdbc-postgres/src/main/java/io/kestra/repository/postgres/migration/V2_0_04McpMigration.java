package io.kestra.repository.postgres.migration;

import java.util.List;

import javax.sql.DataSource;

import io.kestra.jdbc.migration.AbstractSQLMigrationScript;
import io.kestra.repository.postgres.PostgresRepositoryEnabled;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * OSS PostgreSQL MCP migration script.
 *
 * <p>
 * Creates the {@code mcp} and {@code mcp_session} tables for the MCP server integration feature.
 */
@Singleton
@PostgresRepositoryEnabled
public class V2_0_04McpMigration extends AbstractSQLMigrationScript {

    private static final String SCRIPT_ID = "2.0.04-mcp";

    private final DataSource dataSource;

    @Inject
    public V2_0_04McpMigration(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String scriptId() {
        return SCRIPT_ID;
    }

    @Override
    public String description() {
        return "OSS PostgreSQL MCP: create mcp and mcp_session tables";
    }

    @Override
    public List<String> sqlResources() {
        return List.of("/migrations/2.0.04-mcp-postgres.sql");
    }

    @Override
    protected DataSource dataSource() {
        return dataSource;
    }
}
