package io.kestra.repository.h2.migration;

import javax.sql.DataSource;

import io.kestra.core.migration.MigrationScript;
import io.kestra.jdbc.migration.AbstractSQLMigrationScript;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * OSS H2 migration: adds {@code tenant_id} to the {@code executions_start_date} and
 * {@code executions_end_date} indexes to match the MySQL and PostgreSQL definitions.
 */
@Singleton
@Requires(property = "kestra.repository.type", pattern = "h2|memory")
public class V2_0_12ExecutionsDateIndexMigration extends AbstractSQLMigrationScript {

    private static final String SCRIPT_ID = "2.0.12-executions-date-index";
    private static final String RESOURCE = "/migrations/2.0.12-executions-date-index-h2.sql";

    private final DataSource dataSource;

    @Inject
    public V2_0_12ExecutionsDateIndexMigration(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String scriptId() {
        return SCRIPT_ID;
    }

    @Override
    public String description() {
        return "OSS H2: add tenant_id to executions_start_date and executions_end_date indexes";
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
