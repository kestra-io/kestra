package io.kestra.repository.mysql.migration;

import javax.sql.DataSource;

import io.kestra.core.migration.MigrationScript;
import io.kestra.jdbc.migration.AbstractSQLMigrationScript;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * MySQL queue init migration script.
 *
 * <p>
 * Creates the {@code queues} table for the JDBC queue backend when using
 * MySQL queue type. Active independently of the repository backend.
 */
@Singleton
@Requires(property = "kestra.queue.type", value = "mysql")
public class V2_0QueueMigration extends AbstractSQLMigrationScript {

    private static final String SCRIPT_ID = "0-init-queue";

    private final DataSource dataSource;

    @Inject
    public V2_0QueueMigration(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String scriptId() {
        return SCRIPT_ID;
    }

    @Override
    public String description() {
        return "MySQL queue init: create queues table";
    }

    @Override
    public String checksum() {
        return MigrationScript.checksumOfResources("/migrations/baseline-queue-mysql.sql");
    }

    @Override
    public void migrate() throws Exception {
        executeSqlResource(dataSource, "/migrations/baseline-queue-mysql.sql");
    }
}
