package io.kestra.repository.postgres.migration;

import javax.sql.DataSource;

import io.kestra.core.migration.MigrationScript;
import io.kestra.jdbc.migration.AbstractSQLMigrationScript;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * PostgreSQL queue Flyway upgrade migration script.
 *
 * <p>
 * Recreates the {@code queues} table with the Queue 2.0 schema (INT {@code type} column
 * replacing the old ENUM, added {@code routing_key} column) on top of a Flyway-managed schema.
 * The queue is transient: in-flight messages are lost on restart and replayed from executions state,
 * so it is safe to drop and recreate the table.
 *
 * <p>
 * On fresh installations the runner skips this script (schema already exists from the
 * {@code "0-init-queue"} migration). The SQL is idempotent so it is safe to execute in any environment.
 */
@Singleton
@Requires(property = "kestra.queue.type", value = "postgres")
public class V2_0QueueUpgradeMigration extends AbstractSQLMigrationScript {

    private static final String SCRIPT_ID = "2.0-queue";

    private final DataSource dataSource;

    @Inject
    public V2_0QueueUpgradeMigration(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String scriptId() {
        return SCRIPT_ID;
    }

    @Override
    public String description() {
        return "PostgreSQL queue upgrade: recreate queues table with Queue 2.0 schema on Flyway-managed databases";
    }

    @Override
    public String checksum() {
        return MigrationScript.checksumOfResources("/migrations/upgrade-v2.0-queue-postgres.sql");
    }

    @Override
    public void migrate() throws Exception {
        executeSqlResource(dataSource, "/migrations/upgrade-v2.0-queue-postgres.sql");
    }
}
