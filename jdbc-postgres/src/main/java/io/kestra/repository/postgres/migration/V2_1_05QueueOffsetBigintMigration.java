package io.kestra.repository.postgres.migration;

import java.util.List;

import javax.sql.DataSource;

import io.kestra.jdbc.QueueJdbcDataSourceProvider;
import io.kestra.jdbc.migration.AbstractSQLMigrationScript;
import io.kestra.repository.postgres.PostgresQueueEnabled;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Widens the PostgreSQL {@code queues.offset} sequence and column to 64-bit BIGINT.
 */
@Singleton
@PostgresQueueEnabled
public class V2_1_05QueueOffsetBigintMigration extends AbstractSQLMigrationScript {

    private static final String SCRIPT_ID = "2.1.05-queue-offset-bigint";

    private final QueueJdbcDataSourceProvider queueJdbcDataSourceProvider;

    @Inject
    public V2_1_05QueueOffsetBigintMigration(final QueueJdbcDataSourceProvider queueJdbcDataSourceProvider) {
        this.queueJdbcDataSourceProvider = queueJdbcDataSourceProvider;
    }

    @Override
    public String scriptId() {
        return SCRIPT_ID;
    }

    @Override
    public String description() {
        return "PostgreSQL: widen queue offset sequence and column to BIGINT";
    }

    @Override
    protected DataSource dataSource() {
        return queueJdbcDataSourceProvider.dataSource();
    }

    @Override
    public List<String> sqlResources() {
        return List.of("/migrations/2.1.05-queue-offset-bigint-postgres.sql");
    }
}
