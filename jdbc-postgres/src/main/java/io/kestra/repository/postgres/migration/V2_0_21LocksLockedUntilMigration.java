package io.kestra.repository.postgres.migration;

import javax.sql.DataSource;

import io.kestra.core.migration.MigrationScript;
import io.kestra.jdbc.migration.AbstractSQLMigrationScript;
import io.kestra.repository.postgres.PostgresRepositoryEnabled;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * PostgreSQL migration: add a {@code locks.locked_until} generated column so
 * {@code AbstractJdbcLeaseStore} can push the active-lease expiry filter into SQL instead of fetching
 * every row for the category/tenant.
 */
@Singleton
@PostgresRepositoryEnabled
public class V2_0_21LocksLockedUntilMigration extends AbstractSQLMigrationScript {

    private static final String SCRIPT_ID = "2.0.21-locks-locked-until";

    private final DataSource dataSource;

    @Inject
    public V2_0_21LocksLockedUntilMigration(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String scriptId() {
        return SCRIPT_ID;
    }

    @Override
    public String description() {
        return "PostgreSQL: add locks.locked_until generated column for active-lease expiry pushdown";
    }

    @Override
    public String checksum() {
        return MigrationScript.checksumOfResources("/migrations/2.0.21-locks-locked-until-postgres.sql");
    }

    @Override
    public void migrate() throws Exception {
        executeSqlResource(dataSource, "/migrations/2.0.21-locks-locked-until-postgres.sql");
    }
}
