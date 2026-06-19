package io.kestra.repository.postgres.migration;

import javax.sql.DataSource;

import io.kestra.core.migration.MigrationScript;
import io.kestra.jdbc.migration.AbstractSQLMigrationScript;
import io.kestra.repository.postgres.PostgresRepositoryEnabled;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * OSS PostgreSQL migration that fixes the {@code executions.state_duration} generated column.
 *
 * <p>
 * The original expression used {@code EXTRACT(MILLISECONDS FROM interval)}, which only returns the
 * seconds field of the interval and dropped minutes and hours, breaking duration sorting for
 * executions lasting one minute or more. It is replaced with {@code EXTRACT(EPOCH FROM interval) * 1000}
 * so the column stores the total duration in milliseconds.
 */
@Singleton
@PostgresRepositoryEnabled
public class V2_0_11StateDurationFixMigration extends AbstractSQLMigrationScript {

    private static final String SCRIPT_ID = "2.0.11-fix-state-duration";

    private final DataSource dataSource;

    @Inject
    public V2_0_11StateDurationFixMigration(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String scriptId() {
        return SCRIPT_ID;
    }

    @Override
    public String description() {
        return "OSS PostgreSQL: fix executions.state_duration to store the total duration in milliseconds";
    }

    @Override
    public String checksum() {
        return MigrationScript.checksumOfResources("/migrations/2.0.11-fix-state-duration-postgres.sql");
    }

    @Override
    public void migrate() throws Exception {
        executeSqlResource(dataSource, "/migrations/2.0.11-fix-state-duration-postgres.sql");
    }
}
