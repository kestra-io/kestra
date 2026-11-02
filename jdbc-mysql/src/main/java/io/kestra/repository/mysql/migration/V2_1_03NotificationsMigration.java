package io.kestra.repository.mysql.migration;

import java.util.List;

import javax.sql.DataSource;

import io.kestra.jdbc.migration.AbstractSQLMigrationScript;
import io.kestra.repository.mysql.MysqlRepositoryEnabled;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * OSS MySQL migration creating the {@code notifications} table.
 */
@Singleton
@MysqlRepositoryEnabled
public class V2_1_03NotificationsMigration extends AbstractSQLMigrationScript {

    private static final String SCRIPT_ID = "2.1.03-notifications";

    private final DataSource dataSource;

    @Inject
    public V2_1_03NotificationsMigration(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String scriptId() {
        return SCRIPT_ID;
    }

    @Override
    public String description() {
        return "OSS MySQL: create the notifications table";
    }

    @Override
    protected DataSource dataSource() {
        return dataSource;
    }

    @Override
    public List<String> sqlResources() {
        return List.of("/migrations/2.1.03-notifications-mysql.sql");
    }
}
