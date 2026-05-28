package io.kestra.repository.h2.migration;

import javax.sql.DataSource;

import io.kestra.core.migration.MigrationScript;
import io.kestra.jdbc.migration.AbstractSQLMigrationScript;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * H2 queue init migration script.
 *
 * <p>
 * Creates the {@code queues} table for the JDBC queue backend when using
 * H2 or memory queue type. Active independently of the repository backend —
 * this allows the ELS repository to use an H2-backed queue.
 */
@Singleton
@Requires(property = "kestra.queue.type", pattern = "h2|memory")
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
        return "H2 queue init: create queues table";
    }

    @Override
    public String checksum() {
        return MigrationScript.checksumOfResources("/migrations/baseline-queue-h2.sql");
    }

    @Override
    public void migrate() throws Exception {
        executeSqlResource(dataSource, "/migrations/baseline-queue-h2.sql");
    }
}
