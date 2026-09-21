package io.kestra.repository.mysql.migration;

import javax.sql.DataSource;

import io.kestra.jdbc.migration.AbstractV2_0_02QueueMigration;
import io.kestra.repository.mysql.MysqlQueueEnabled;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * MySQL Queue 2.0 upgrade migration.
 */
@Singleton
@MysqlQueueEnabled
public class V2_0_02QueueMigration extends AbstractV2_0_02QueueMigration {

    private final DataSource dataSource;

    @Inject
    public V2_0_02QueueMigration(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    protected DataSource dataSource() {
        return dataSource;
    }

    @Override
    protected String dropLegacyResource() {
        return "/migrations/2.0.02-queue-drop-legacy-mysql.sql";
    }

    @Override
    protected String convergeResource() {
        return "/migrations/2.0.02-queue-mysql.sql";
    }
}
