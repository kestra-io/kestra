package io.kestra.repository.h2.migration;

import io.kestra.core.migration.MigrationScript;
import io.kestra.jdbc.migration.AbstractSQLMigrationScript;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import javax.sql.DataSource;

/**
 * H2 queue Flyway update migration script.
 *
 * <p>
 * Update the {@code queues} by replacing two indices by a single one and switching the type column to a VARCHAR
 */
@Singleton
@Requires(property = "kestra.queue.type", pattern = "h2|memory")
public class V2_0QueueIndexAndType extends AbstractSQLMigrationScript {

    private static final String SCRIPT_ID = "2.0.1-queue-index-and-type";

    private final DataSource dataSource;

    @Inject
    public V2_0QueueIndexAndType(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String scriptId() {
        return SCRIPT_ID;
    }

    @Override
    public String description() {
        return "H2 queue upgrade: update queue indices and change type to be a string";
    }

    @Override
    public String checksum() {
        return MigrationScript.checksumOfResources("/migrations/2.0.1-queue-index-and-type-h2.sql");
    }

    @Override
    public void migrate() throws Exception {
        executeSqlResource(dataSource, "/migrations/2.0.1-queue-index-and-type-h2.sql");
    }
}
