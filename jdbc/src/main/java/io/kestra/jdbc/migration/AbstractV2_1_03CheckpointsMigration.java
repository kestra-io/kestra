package io.kestra.jdbc.migration;

/**
 * Abstract base for the 2.1.03 checkpoints migration, which creates the {@code checkpoints} table.
 */
public abstract class AbstractV2_1_03CheckpointsMigration extends AbstractSQLMigrationScript {

    @Override
    public String scriptId() {
        return "2.1.03-checkpoints";
    }

    @Override
    public String description() {
        return "Task runs: store the checkpoints in a dedicated table";
    }
}
