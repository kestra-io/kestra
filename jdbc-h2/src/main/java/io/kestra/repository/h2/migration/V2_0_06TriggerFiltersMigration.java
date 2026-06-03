package io.kestra.repository.h2.migration;

import javax.sql.DataSource;

import io.kestra.core.migration.MigrationScript;
import io.kestra.jdbc.migration.AbstractSQLMigrationScript;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * OSS H2 migration: adds {@code type} and {@code last_triggered_date} generated columns
 * to the {@code triggers} table and creates the corresponding indexes for trigger filter queries.
 */
@Singleton
@Requires(property = "kestra.repository.type", pattern = "h2|memory")
public class V2_0_06TriggerFiltersMigration extends AbstractSQLMigrationScript {

    private static final String SCRIPT_ID = "2.0.06-trigger-filters";
    private static final String RESOURCE = "/migrations/2.0.06-trigger-filters-h2.sql";

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
        return "OSS H2: add type and last_triggered_date columns to triggers table";
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
