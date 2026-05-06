package io.kestra.repository.h2.migration;

import javax.sql.DataSource;

import io.kestra.core.migration.MigrationScript;
import io.kestra.jdbc.migration.AbstractSQLMigrationScript;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * OSS H2 MCP migration script.
 *
 * <p>
 * Creates the {@code mcp} and {@code mcp_session} tables for the MCP server integration feature.
 * Activates only when H2 is the repository backend.
 */
@Singleton
@Requires(property = "kestra.repository.type", pattern = "h2|memory")
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
        return "OSS H2 MCP: create mcp and mcp_session tables";
    }

    @Override
    public String checksum() {
        return MigrationScript.checksumOfResources("/migrations/upgrade-v2.0-mcp-h2.sql");
    }

    @Override
    public void migrate() throws Exception {
        executeSqlResource(dataSource, "/migrations/upgrade-v2.0-mcp-h2.sql");
    }
}
