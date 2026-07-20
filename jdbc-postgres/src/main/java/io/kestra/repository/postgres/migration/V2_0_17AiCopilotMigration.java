package io.kestra.repository.postgres.migration;

import java.util.List;

import javax.sql.DataSource;

import io.kestra.jdbc.migration.AbstractSQLMigrationScript;
import io.kestra.repository.postgres.PostgresRepositoryEnabled;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * OSS PostgreSQL AI Copilot migration script.
 *
 * <p>
 * Creates the {@code ai_agent_thread} and {@code ai_agent_message} tables backing the Copilot conversation
 * store.
 */
@Singleton
@PostgresRepositoryEnabled
public class V2_0_17AiCopilotMigration extends AbstractSQLMigrationScript {

    private static final String SCRIPT_ID = "2.0.17-ai-copilot";

    private final DataSource dataSource;

    @Inject
    public V2_0_17AiCopilotMigration(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String scriptId() {
        return SCRIPT_ID;
    }

    @Override
    public String description() {
        return "OSS PostgreSQL AI Copilot: create ai_agent_thread and ai_agent_message tables";
    }

    @Override
    public List<String> sqlResources() {
        return List.of("/migrations/2.0.17-ai-copilot-postgres.sql");
    }

    @Override
    protected DataSource dataSource() {
        return dataSource;
    }
}
