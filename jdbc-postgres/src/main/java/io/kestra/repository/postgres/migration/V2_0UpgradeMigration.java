package io.kestra.repository.postgres.migration;

import io.kestra.jdbc.migration.AbstractSQLMigrationScript;
import io.kestra.repository.postgres.PostgresRepositoryEnabled;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import javax.sql.DataSource;

/**
 * OSS PostgreSQL Flyway upgrade migration script.
 *
 * <p>Applies schema changes introduced in Kestra 2.0 on top of a Flyway-managed schema
 * (Kestra &le; 1.3): drops {@code templates} and {@code executorstate}, creates {@code locks}
 * and {@code task_outputs}, adds scheduler VNode columns on {@code triggers}, adds
 * {@code trigger_id} on {@code executions}, and renames {@code worker_uuid} to {@code worker_uid}
 * on {@code worker_job_running}.
 *
 * <p>On fresh installations the runner skips this script (schema already exists from the
 * {@code "0-init"} migration). The SQL is idempotent ({@code IF NOT EXISTS} / {@code IF EXISTS})
 * so it is safe to execute in any environment.
 */
@Singleton
@PostgresRepositoryEnabled
public class V2_0UpgradeMigration extends AbstractSQLMigrationScript {

    private static final String SCRIPT_ID = "2.0";
    private static final String CHECKSUM = "postgres-upgrade-v2.0";

    private final DataSource dataSource;

    @Inject
    public V2_0UpgradeMigration(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String scriptId() {
        return SCRIPT_ID;
    }

    @Override
    public String description() {
        return "OSS PostgreSQL upgrade: apply Kestra 2.0 schema changes on Flyway-managed databases";
    }

    @Override
    public String checksum() {
        return CHECKSUM;
    }

    @Override
    public void migrate() throws Exception {
        executeSqlResource(dataSource, "/migrations/upgrade-v2.0-postgres.sql");
    }
}
