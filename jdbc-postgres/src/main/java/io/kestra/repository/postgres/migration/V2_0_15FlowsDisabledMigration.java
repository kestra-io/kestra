package io.kestra.repository.postgres.migration;

import javax.sql.DataSource;

import io.kestra.core.migration.MigrationScript;
import io.kestra.jdbc.migration.AbstractSQLMigrationScript;
import io.kestra.repository.postgres.PostgresRepositoryEnabled;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Adds a generated {@code disabled} column on the {@code flows} table (derived from the JSON value).
 */
@Singleton
@PostgresRepositoryEnabled
public class V2_0_15FlowsDisabledMigration extends AbstractSQLMigrationScript {

    private static final String SCRIPT_ID = "2.0.15-flows-disabled";

    private final DataSource dataSource;

    @Inject
    public V2_0_15FlowsDisabledMigration(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String scriptId() {
        return SCRIPT_ID;
    }

    @Override
    public String description() {
        return "OSS PostgreSQL flows disabled: add generated disabled column";
    }

    @Override
    public String checksum() {
        return MigrationScript.checksumOfResources("/migrations/2.0.15-flows-disabled-postgres.sql");
    }

    @Override
    public void migrate() throws Exception {
        executeSqlResource(dataSource, "/migrations/2.0.15-flows-disabled-postgres.sql");
    }
}
