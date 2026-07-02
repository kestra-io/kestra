package io.kestra.repository.postgres.migration;

import javax.sql.DataSource;

import io.kestra.core.migration.MigrationScript;
import io.kestra.jdbc.migration.AbstractSQLMigrationScript;
import io.kestra.repository.postgres.PostgresRepositoryEnabled;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * OSS PostgreSQL flows draft migration script.
 *
 * <p>
 * Adds a generated {@code draft} column on the {@code flows} table (derived from the JSON value)
 * so the "latest non-draft revision" query can filter on it directly.
 */
@Singleton
@PostgresRepositoryEnabled
public class V2_0_13FlowsDraftMigration extends AbstractSQLMigrationScript {

    private static final String SCRIPT_ID = "2.0.13-flows-draft";

    private final DataSource dataSource;

    @Inject
    public V2_0_13FlowsDraftMigration(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String scriptId() {
        return SCRIPT_ID;
    }

    @Override
    public String description() {
        return "OSS PostgreSQL flows draft: add generated draft column and index";
    }

    @Override
    public String checksum() {
        return MigrationScript.checksumOfResources("/migrations/2.0.13-flows-draft-postgres.sql");
    }

    @Override
    public void migrate() throws Exception {
        executeSqlResource(dataSource, "/migrations/2.0.13-flows-draft-postgres.sql");
    }
}
