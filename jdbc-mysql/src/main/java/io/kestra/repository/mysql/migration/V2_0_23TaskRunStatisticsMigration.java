package io.kestra.repository.mysql.migration;

import javax.sql.DataSource;

import io.kestra.core.migration.MigrationScript;
import io.kestra.jdbc.migration.AbstractSQLMigrationScript;
import io.kestra.repository.mysql.MysqlRepositoryEnabled;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * OSS Mysql task-run-statistics migration script.
 *
 * <p>
 * Adds the {@code task_run_statistics} table, used to store per-task-run (raw) and periodically
 * compacted (aggregate) task-run-statistic rows, aggregated to the minute.
 */
@Singleton
@MysqlRepositoryEnabled
public class V2_0_23TaskRunStatisticsMigration extends AbstractSQLMigrationScript{

    private static final String SCRIPT_ID = "2.0.23-task-run-statistics";
    private final DataSource dataSource;

    @Inject
    public V2_0_23TaskRunStatisticsMigration(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String scriptId() {
        return SCRIPT_ID;
    }

    @Override
    public String description() {
        return "OSS Mysql: add the task_run_statistics table";
    }

    @Override
    public String checksum() {
        return MigrationScript.checksumOfResources("/migrations/2.0.23-task-run-statistics-mysql.sql");
    }

    @Override
    public void migrate() throws Exception {
        executeSqlResource(dataSource, "/migrations/2.0.23-task-run-statistics-mysql.sql");
    }
}
