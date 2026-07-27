package io.kestra.repository.postgres.migration;

import javax.sql.DataSource;

import io.kestra.core.migration.MigrationScript;
import io.kestra.jdbc.migration.AbstractSQLMigrationScript;
import io.kestra.repository.postgres.PostgresRepositoryEnabled;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * PostgreSQL migration: widen {@code locks.key} (250 -> 700) and {@code locks.id} (150 -> 500) so
 * asset leases, whose key is {@code Lease.key(category, tenantId, id)} (id up to 150), fit. The
 * shared {@code locks} table was sized for short server-mutex keys.
 */
@Singleton
@PostgresRepositoryEnabled
public class V2_0_19WidenLocksColumnsMigration extends AbstractSQLMigrationScript {

    private static final String SCRIPT_ID = "2.0.19-widen-locks";

    private final DataSource dataSource;

    @Inject
    public V2_0_19WidenLocksColumnsMigration(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String scriptId() {
        return SCRIPT_ID;
    }

    @Override
    public String description() {
        return "PostgreSQL: widen locks.key (250->700) and locks.id (150->500) to fit asset lease ids";
    }

    @Override
    public String checksum() {
        return MigrationScript.checksumOfResources("/migrations/2.0.19-widen-locks-postgres.sql");
    }

    @Override
    public void migrate() throws Exception {
        executeSqlResource(dataSource, "/migrations/2.0.19-widen-locks-postgres.sql");
    }
}
