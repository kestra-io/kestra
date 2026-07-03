package io.kestra.repository.postgres.migration;

import java.util.List;

import javax.sql.DataSource;

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
public class V2_0_05QueueUpgradeMigration extends AbstractSQLMigrationScript {

    private static final String SCRIPT_ID = "2.0.05-queue";

    private final DataSource dataSource;

    @Inject
    public V2_0_05QueueUpgradeMigration(final DataSource dataSource) {
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
    public List<String> sqlResources() {
        return List.of("/migrations/2.0.05-queue-postgres.sql");
    }

    @Override
    protected DataSource dataSource() {
        return dataSource;
    }
}
