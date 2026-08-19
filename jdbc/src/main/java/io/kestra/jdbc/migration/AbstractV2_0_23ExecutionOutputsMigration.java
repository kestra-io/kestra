package io.kestra.jdbc.migration;

/**
 * Abstract base for the 2.0.23 execution outputs migration.
 *
 * <p>
 * Creates the {@code execution_outputs} table which stores the flow-level outputs of an execution outside of the
 * execution itself, so that large outputs no longer bloat the execution record. The table is keyed by the execution
 * identifier, so the generic {@code key} column is enough to look up and purge outputs: no secondary index is needed.
 */
public abstract class AbstractV2_0_23ExecutionOutputsMigration extends AbstractSQLMigrationScript {

    @Override
    public String scriptId() {
        return "2.0.23-execution-outputs";
    }

    @Override
    public String description() {
        return "Executions: store the execution outputs in a dedicated table";
    }
}
