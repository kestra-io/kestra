package io.kestra.repository.mysql.migration;

import javax.sql.DataSource;

import io.kestra.core.migration.MigrationScript;
import io.kestra.jdbc.migration.AbstractSQLMigrationScript;
import io.kestra.repository.mysql.MysqlRepositoryEnabled;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * OSS MySQL migration: adds {@code type} and {@code last_triggered_date} generated columns
 * to the {@code triggers} table and creates the corresponding indexes for trigger filter queries.
 */
@Singleton
@MysqlRepositoryEnabled
public class V2_0_06TriggerFiltersMigration extends AbstractSQLMigrationScript {

    private static final String SCRIPT_ID = "2.0.06-trigger-filters";
    private static final String RESOURCE = "/migrations/2.0.06-trigger-filters-mysql.sql";

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
        return "OSS MySQL: add type and last_triggered_date columns to triggers table";
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
