package io.kestra.repository.mysql.migration;

import java.util.List;

import javax.sql.DataSource;

import io.kestra.jdbc.migration.AbstractSQLMigrationScript;
import io.kestra.repository.mysql.MysqlRepositoryEnabled;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * OSS MySQL AI Copilot migration script.
 *
 * <p>
 * Creates the {@code ai_agent_thread} and {@code ai_agent_message} tables backing the Copilot conversation
 * store.
 */
@Singleton
@MysqlRepositoryEnabled
public class V2_0_19AiCopilotMigration extends AbstractSQLMigrationScript {

    private static final String SCRIPT_ID = "2.0.19-ai-copilot";

    private final DataSource dataSource;

    @Inject
    public V2_0_19AiCopilotMigration(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String scriptId() {
        return SCRIPT_ID;
    }

    @Override
    public String description() {
        return "OSS MySQL AI Copilot: create ai_agent_thread and ai_agent_message tables";
    }

    @Override
    public List<String> sqlResources() {
        return List.of("/migrations/2.0.19-ai-copilot-mysql.sql");
    }

    @Override
    protected DataSource dataSource() {
        return dataSource;
    }
}
