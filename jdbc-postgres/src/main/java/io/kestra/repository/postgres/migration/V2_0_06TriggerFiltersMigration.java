package io.kestra.repository.postgres.migration;

import javax.sql.DataSource;

import io.kestra.core.migration.MigrationScript;
import io.kestra.jdbc.migration.AbstractSQLMigrationScript;
import io.kestra.repository.postgres.PostgresRepositoryEnabled;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * OSS PostgreSQL migration: adds {@code type} and {@code last_triggered_date} generated columns
 * to the {@code triggers} table and creates the corresponding indexes for trigger filter queries.
 */
@Singleton
@PostgresRepositoryEnabled
public class V2_0_06TriggerFiltersMigration extends AbstractSQLMigrationScript {

    private static final String SCRIPT_ID = "2.0.06-trigger-filters";
    private static final String RESOURCE = "/migrations/2.0.06-trigger-filters-postgres.sql";

    private final DataSource dataSource;

    @Inject
    public V2_0_06TriggerFiltersMigration(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String scriptId() {
        return SCRIPT_ID;
    }

    @Override
    public String description() {
        return "OSS PostgreSQL: add type and last_triggered_date columns to triggers table";
    }

    @Override
    public String checksum() {
        return MigrationScript.checksumOfResources(RESOURCE);
    }

    @Override
    public void migrate() throws Exception {
        executeSqlResource(dataSource, RESOURCE);
    }
}
