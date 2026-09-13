package io.kestra.repository.postgres.migration;

import javax.sql.DataSource;

import io.kestra.jdbc.migration.AbstractV2_0_02QueueMigration;
import io.kestra.repository.postgres.PostgresQueueEnabled;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * PostgreSQL Queue 2.0 upgrade migration.
 */
@Singleton
@PostgresQueueEnabled
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
        return "/migrations/2.0.02-queue-drop-legacy-postgres.sql";
    }

    @Override
    protected String convergeResource() {
        return "/migrations/2.0.02-queue-postgres.sql";
    }
}
