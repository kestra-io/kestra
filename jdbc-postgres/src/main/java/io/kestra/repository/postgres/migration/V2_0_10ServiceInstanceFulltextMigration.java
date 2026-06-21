package io.kestra.repository.postgres.migration;

import javax.sql.DataSource;

import io.kestra.core.migration.MigrationScript;
import io.kestra.jdbc.migration.AbstractSQLMigrationScript;
import io.kestra.repository.postgres.PostgresRepositoryEnabled;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * OSS PostgreSQL migration: adds a generated {@code fulltext} TSVECTOR column to
 * {@code service_instance} for the query (free-text) filter, mirroring the full-text pattern of the
 * other repositories.
 */
@Singleton
@PostgresRepositoryEnabled
public class V2_0_10ServiceInstanceFulltextMigration extends AbstractSQLMigrationScript {

    private static final String SCRIPT_ID = "2.0.10-service-instance-fulltext";
    private static final String RESOURCE = "/migrations/2.0.10-service-instance-fulltext-postgres.sql";

    private final DataSource dataSource;

    @Inject
    public V2_0_10ServiceInstanceFulltextMigration(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String scriptId() {
        return SCRIPT_ID;
    }

    @Override
    public String description() {
        return "OSS PostgreSQL: add generated fulltext column to service_instance for the query filter";
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
