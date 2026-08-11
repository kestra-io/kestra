package io.kestra.repository.postgres.migration;

import java.util.List;

import javax.sql.DataSource;

import io.kestra.jdbc.migration.AbstractSQLMigrationScript;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * PostgreSQL queue init migration script.
 *
 * <p>
 * Creates the {@code queues} table for the JDBC queue backend when using
 * PostgreSQL queue type. Active independently of the repository backend.
 */
@Singleton
@Requires(property = "kestra.queue.type", value = "postgres")
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
        return "PostgreSQL queue init: create queues table";
    }

    @Override
    public List<String> sqlResources() {
        return List.of("/migrations/baseline-queue-postgres.sql");
    }

    @Override
    protected DataSource dataSource() {
        return dataSource;
    }
}
