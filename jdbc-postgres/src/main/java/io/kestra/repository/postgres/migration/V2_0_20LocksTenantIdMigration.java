package io.kestra.repository.postgres.migration;

import javax.sql.DataSource;

import io.kestra.core.migration.MigrationScript;
import io.kestra.jdbc.migration.AbstractSQLMigrationScript;
import io.kestra.repository.postgres.PostgresRepositoryEnabled;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * PostgreSQL migration: add a nullable {@code locks.tenant_id} generated column so
 * {@code AbstractJdbcLeaseStore}'s shared {@code buildTenantCondition(tenantId)} filter, which
 * expects that column on every tenant-scoped table, can run against the shared {@code locks} table.
 */
@Singleton
@PostgresRepositoryEnabled
public class V2_0_20LocksTenantIdMigration extends AbstractSQLMigrationScript {

    private static final String SCRIPT_ID = "2.0.20-locks-tenant-id";

    private final DataSource dataSource;

    @Inject
    public V2_0_20LocksTenantIdMigration(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String scriptId() {
        return SCRIPT_ID;
    }

    @Override
    public String description() {
        return "PostgreSQL: add nullable locks.tenant_id generated column for tenant-scoped lease queries";
    }

    @Override
    public String checksum() {
        return MigrationScript.checksumOfResources("/migrations/2.0.20-locks-tenant-id-postgres.sql");
    }

    @Override
    public void migrate() throws Exception {
        executeSqlResource(dataSource, "/migrations/2.0.20-locks-tenant-id-postgres.sql");
    }
}
