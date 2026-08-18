package io.kestra.jdbc.migration;

/**
 * Abstract base for the 2.0.23 execution indexed fields migration.
 *
 * <p>
 * Creates the {@code execution_indexed_fields} table that stores per-execution key/value pairs (plain string values)
 * computed from flow-declared Pebble expressions at execution end, so executions can be searched efficiently without
 * indexing the full task or flow outputs.
 */
public abstract class AbstractV2_0_23ExecutionIndexedFieldsMigration extends AbstractSQLMigrationScript {

    @Override
    public String scriptId() {
        return "2.0.23-execution-indexed-fields";
    }

    @Override
    public String description() {
        return "Create the execution_indexed_fields table for searchable indexed fields";
    }
}
