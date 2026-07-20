package io.kestra.repository.h2.migration;

import java.util.List;

import javax.sql.DataSource;

import io.kestra.jdbc.migration.AbstractSQLMigrationScript;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * OSS H2 AI Copilot migration script.
 *
 * <p>
 * Creates the {@code ai_agent_thread} and {@code ai_agent_message} tables backing the Copilot conversation
 * store. Activates only when H2 (or the in-memory H2) is the repository backend.
 */
@Singleton
@Requires(property = "kestra.repository.type", pattern = "h2|memory")
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
        return "OSS H2 AI Copilot: create ai_agent_thread and ai_agent_message tables";
    }

    @Override
    public List<String> sqlResources() {
        return List.of("/migrations/2.0.19-ai-copilot-h2.sql");
    }

    @Override
    protected DataSource dataSource() {
        return dataSource;
    }
}
