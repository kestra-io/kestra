package io.kestra.repository.mysql.migration;

import java.util.List;

import javax.sql.DataSource;

import io.kestra.jdbc.migration.AbstractSQLMigrationScript;
import io.kestra.repository.mysql.MysqlQueueEnabled;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * MySQL queue Flyway update migration script.
 *
 * <p>
 * Update the {@code queues} by replacing two indices by a single one and switching the type column to a VARCHAR
 */
@Singleton
@MysqlQueueEnabled
public class V2_0_09QueueIndexAndTypeMigration extends AbstractSQLMigrationScript {

    private static final String SCRIPT_ID = "2.0.09-queue-index-and-type";

    private final DataSource dataSource;

    @Inject
    public V2_0_09QueueIndexAndTypeMigration(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String scriptId() {
        return SCRIPT_ID;
    }

    @Override
    public String description() {
        return "MySQL queue upgrade: update queue indices and change type to be a string";
    }

    @Override
    public List<String> sqlResources() {
        return List.of("/migrations/2.0.09-queue-index-and-type-mysql.sql");
    }

    @Override
    protected DataSource dataSource() {
        return dataSource;
    }
}
