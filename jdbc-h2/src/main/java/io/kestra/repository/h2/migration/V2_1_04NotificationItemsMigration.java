package io.kestra.repository.h2.migration;

import java.util.List;

import javax.sql.DataSource;

import io.kestra.jdbc.migration.AbstractSQLMigrationScript;
import io.kestra.repository.h2.H2RepositoryEnabled;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * OSS H2 migration creating the {@code notification_items} table.
 */
@Singleton
@H2RepositoryEnabled
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
        return "OSS H2: create the notification_items table";
    }

    @Override
    protected DataSource dataSource() {
        return dataSource;
    }

    @Override
    public List<String> sqlResources() {
        return List.of("/migrations/2.1.04-notification-items-h2.sql");
    }
}
