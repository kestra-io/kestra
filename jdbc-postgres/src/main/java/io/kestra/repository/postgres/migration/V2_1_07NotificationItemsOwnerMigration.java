package io.kestra.repository.postgres.migration;

import java.util.List;

import javax.sql.DataSource;

import io.kestra.jdbc.migration.AbstractSQLMigrationScript;
import io.kestra.repository.postgres.PostgresRepositoryEnabled;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * OSS Postgres migration adding the {@code owner} column to {@code notification_items}.
 */
@Singleton
@PostgresRepositoryEnabled
public class V2_1_07NotificationItemsOwnerMigration extends AbstractSQLMigrationScript {

    private static final String SCRIPT_ID = "2.1.07-notification-items-owner";

    private final DataSource dataSource;

    @Inject
    public V2_1_07NotificationItemsOwnerMigration(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String scriptId() {
        return SCRIPT_ID;
    }

    @Override
    public String description() {
        return "OSS Postgres: add the owner column to notification_items";
    }

    @Override
    protected DataSource dataSource() {
        return dataSource;
    }

    @Override
    public List<String> sqlResources() {
        return List.of("/migrations/2.1.07-notification-items-owner-postgres.sql");
    }
}
