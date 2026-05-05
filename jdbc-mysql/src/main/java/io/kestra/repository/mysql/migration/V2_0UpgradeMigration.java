package io.kestra.repository.mysql.migration;

import javax.sql.DataSource;

import io.kestra.core.migration.AbstractV2UpgradeMigration;
import io.kestra.core.migration.MigrationScript;
import io.kestra.jdbc.migration.AbstractSQLMigrationScript;
import io.kestra.repository.mysql.MysqlRepositoryEnabled;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * OSS MySQL Flyway upgrade migration script.
 *
 * <p>
 * Applies schema changes introduced in Kestra 2.0 on top of a Flyway-managed schema
 * (Kestra &le; 1.3): drops {@code templates} and {@code executorstate}, creates {@code locks}
 * and {@code task_outputs}, adds scheduler VNode columns on {@code triggers}, adds
 * {@code trigger_id} on {@code executions}, and renames {@code worker_uuid} to {@code worker_uid}
 * on {@code worker_job_running}. Also migrates all V1 trigger rows to TriggerState.
 *
 * <p>
 * On fresh installations the runner skips this script (schema already exists from the
 * {@code "0-init"} migration). The SQL is idempotent ({@code IF NOT EXISTS} / {@code IF EXISTS})
 * so it is safe to execute in any environment.
 */
@Singleton
@MysqlRepositoryEnabled
public class V2_0UpgradeMigration extends AbstractV2UpgradeMigration {

    private final DataSource dataSource;

    @Inject
    public V2_0UpgradeMigration(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String description() {
        return "OSS MySQL upgrade: apply Kestra 2.0 schema changes on Flyway-managed databases";
    }

    @Override
    public String checksum() {
        return MigrationScript.checksumOfResources("/migrations/upgrade-v2.0-mysql.sql");
    }

    @Override
    protected void doSchemaUpgrade() throws Exception {
        AbstractSQLMigrationScript.executeSqlScript(dataSource, "/migrations/upgrade-v2.0-mysql.sql");
    }
}
