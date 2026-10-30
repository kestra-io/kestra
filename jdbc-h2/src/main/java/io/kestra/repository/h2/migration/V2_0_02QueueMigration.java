package io.kestra.repository.h2.migration;

import javax.sql.DataSource;

import io.kestra.jdbc.QueueJdbcDataSourceProvider;
import io.kestra.jdbc.migration.AbstractV2_0_02QueueMigration;
import io.kestra.repository.h2.H2QueueEnabled;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * H2 Queue 2.0 upgrade migration.
 */
@Singleton
@H2QueueEnabled
public class V2_0_02QueueMigration extends AbstractV2_0_02QueueMigration {

    private final QueueJdbcDataSourceProvider queueJdbcDataSourceProvider;

    @Inject
    public V2_0_02QueueMigration(final QueueJdbcDataSourceProvider queueJdbcDataSourceProvider) {
        this.queueJdbcDataSourceProvider = queueJdbcDataSourceProvider;
    }

    @Override
    protected DataSource dataSource() {
        return queueJdbcDataSourceProvider.dataSource();
    }

    @Override
    protected String dropLegacyResource() {
        return "/migrations/2.0.02-queue-drop-legacy-h2.sql";
    }

    @Override
    protected String convergeResource() {
        return "/migrations/2.0.02-queue-h2.sql";
    }
}
