package io.kestra.repository.postgres.migration;

import javax.sql.DataSource;

import io.kestra.core.migration.MigrationScript;
import io.kestra.jdbc.migration.AbstractSQLMigrationScript;
import io.kestra.repository.postgres.PostgresQueueEnabled;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * PostgreSQL queue Flyway update migration script.
 *
 * <p>
 * Replaces the {@code (created, type)} queue index with {@code (type, created)} so the queue
 * cleaner's per-type delete ({@code WHERE created <= ? AND type = ?}) can seek directly instead
 * of scanning the whole {@code created <= threshold} range once per queue type.
 */
@Singleton
@PostgresQueueEnabled
public class V2_0_13QueueCleanerIndexMigration extends AbstractSQLMigrationScript {

    private static final String SCRIPT_ID = "2.0.13-queue-cleaner-index";

    private final DataSource dataSource;

    @Inject
    public V2_0_13QueueCleanerIndexMigration(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String scriptId() {
        return SCRIPT_ID;
    }

    @Override
    public String description() {
        return "Postgres queue upgrade: reorder the queue cleaner index to (type, created)";
    }

    @Override
    public String checksum() {
        return MigrationScript.checksumOfResources("/migrations/2.0.13-queue-cleaner-index-postgres.sql");
    }

    @Override
    public void migrate() throws Exception {
        executeSqlResource(dataSource, "/migrations/2.0.13-queue-cleaner-index-postgres.sql");
    }
}
