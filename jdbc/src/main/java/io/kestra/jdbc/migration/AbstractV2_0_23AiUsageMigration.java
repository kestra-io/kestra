package io.kestra.jdbc.migration;

public abstract class AbstractV2_0_23AiUsageMigration extends AbstractSQLMigrationScript {
    @Override
    public String scriptId() {
        return "2.0.23-ai-usage";
    }

    @Override
    public String description() {
        return "AI: record token usage per model call, per provider";
    }
}
