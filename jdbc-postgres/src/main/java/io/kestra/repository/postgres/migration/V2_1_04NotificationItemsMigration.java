package io.kestra.repository.postgres.migration;

import java.util.List;

import javax.sql.DataSource;

import io.kestra.jdbc.migration.AbstractSQLMigrationScript;
import io.kestra.repository.postgres.PostgresRepositoryEnabled;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * OSS Postgres migration creating the {@code notification_items} table.
 */
@Singleton
@PostgresRepositoryEnabled
public class V2_1_04NotificationItemsMigration extends AbstractSQLMigrationScript {

    private static final String SCRIPT_ID = "2.1.04-notification-items";

    private final DataSource dataSource;

    @Inject
    public V2_1_04NotificationItemsMigration(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String scriptId() {
        return SCRIPT_ID;
    }

    @Override
    public String description() {
        return "OSS Postgres: create the notification_items table";
    }

    @Override
    protected DataSource dataSource() {
        return dataSource;
    }

    @Override
    public List<String> sqlResources() {
        return List.of("/migrations/2.1.04-notification-items-postgres.sql");
    }
}
