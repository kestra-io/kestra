package io.kestra.jdbc.migration;

/**
 * Abstract base for the 2.0.25 trigger scheduler index migration.
 *
 * <p>
 * Adds {@code type} to {@code idx_trigger_scheduler}. The scheduler's eligibility query now excludes the
 * triggers it never evaluates, whose state carries no next evaluation date and no lock: they match the leading
 * columns of the index and were rejected only after visiting the row. Carrying the type in the index rejects
 * them from the index alone, so listing many webhook, MCP or flow triggers no longer costs a heap fetch on
 * every scheduling loop.
 */
public abstract class AbstractV2_0_25TriggerSchedulerIndexMigration extends AbstractSQLMigrationScript {

    @Override
    public String scriptId() {
        return "2.0.25-trigger-scheduler-index";
    }

    @Override
    public String description() {
        return "Triggers: cover the trigger type in the scheduler index";
    }
}
