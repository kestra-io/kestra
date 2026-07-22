package io.kestra.repository.mysql.migration;

import javax.sql.DataSource;

import io.kestra.core.migration.MigrationScript;
import io.kestra.jdbc.migration.AbstractSQLMigrationScript;
import io.kestra.repository.mysql.MysqlRepositoryEnabled;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * OSS MySQL execution-statistics migration script.
 *
 * <p>
 * Adds the {@code execution_statistics} table, used to store per-execution (raw) and periodically
 * compacted (aggregate) execution-statistic rows, aggregated to the minute (see issue #16524).
 */
@Singleton
@MysqlRepositoryEnabled
public class V2_0_14ExecutionStatisticsMigration extends AbstractSQLMigrationScript {

    private static final String SCRIPT_ID = "2.0.14-execution-statistics";

    private final DataSource dataSource;

    @Inject
    public V2_0_14ExecutionStatisticsMigration(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String scriptId() {
        return SCRIPT_ID;
    }

    @Override
    public String description() {
        return "OSS MySQL: add the execution_statistics table";
    }

    @Override
    public String checksum() {
        return MigrationScript.checksumOfResources("/migrations/2.0.14-execution-statistics-mysql.sql");
    }

    @Override
    public void migrate() throws Exception {
        executeSqlResource(dataSource, "/migrations/2.0.14-execution-statistics-mysql.sql");
    }
}
