package io.kestra.jdbc.migration;

/**
 * Abstract base for the Kestra 2.0 schema upgrade migration, applied on top of the frozen 1.3-era
 * baseline. Every statement is idempotent, so this also runs safely on a fresh install
 * (immediately after the {@code "0-init"} baseline) and on an already-migrated 2.0.0-rcN database.
 */
public abstract class AbstractV2_0_01SchemaMigration extends AbstractSQLMigrationScript {

    @Override
    public String scriptId() {
        return "2.0.01-schema";
    }

    @Override
    public String description() {
        return "Kestra 2.0 schema upgrade";
    }
}
