package io.kestra.jdbc.migration;

public abstract class AbstractV2_1_09AiUsageMigration extends AbstractSQLMigrationScript {
    @Override
    public String scriptId() {
        return "2.1.09-ai-usage";
    }

    @Override
    public String description() {
        return "AI: record token usage per model call, per provider";
    }
}
