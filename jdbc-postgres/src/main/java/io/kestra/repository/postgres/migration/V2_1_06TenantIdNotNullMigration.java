package io.kestra.repository.postgres.migration;

import java.util.List;

import javax.sql.DataSource;

import io.kestra.jdbc.migration.AbstractSQLMigrationScript;
import io.kestra.repository.postgres.PostgresRepositoryEnabled;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * OSS Postgres migration adding NOT NULL constraint to tenant_id on all tenant-scoped tables.
 */
@Singleton
@PostgresRepositoryEnabled
public class V2_1_06TenantIdNotNullMigration extends AbstractSQLMigrationScript {

    private static final String SCRIPT_ID = "2.1.06-tenant-id-not-null";

    private final DataSource dataSource;

    @Inject
    public V2_1_06TenantIdNotNullMigration(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String scriptId() {
        return SCRIPT_ID;
    }

    @Override
    public String description() {
        return "OSS Postgres: add NOT NULL constraint to tenant_id on all tenant-scoped tables";
    }

    @Override
    protected DataSource dataSource() {
        return dataSource;
    }

    @Override
    public List<String> sqlResources() {
        return List.of("/migrations/2.1.06-tenant-id-not-null-postgres.sql");
    }
}
