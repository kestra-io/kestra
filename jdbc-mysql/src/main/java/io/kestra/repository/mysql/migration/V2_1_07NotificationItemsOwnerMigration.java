package io.kestra.repository.mysql.migration;

import java.util.List;

import javax.sql.DataSource;

import io.kestra.jdbc.migration.AbstractSQLMigrationScript;
import io.kestra.repository.mysql.MysqlRepositoryEnabled;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * OSS MySQL migration adding the {@code owner} column to {@code notification_items}.
 */
@Singleton
@MysqlRepositoryEnabled
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
        return "OSS MySQL: add the owner column to notification_items";
    }

    @Override
    protected DataSource dataSource() {
        return dataSource;
    }

    @Override
    public List<String> sqlResources() {
        return List.of("/migrations/2.1.07-notification-items-owner-mysql.sql");
    }
}
