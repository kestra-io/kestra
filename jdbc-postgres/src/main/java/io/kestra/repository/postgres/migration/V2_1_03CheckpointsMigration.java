package io.kestra.repository.postgres.migration;

import java.util.List;

import javax.sql.DataSource;

import io.kestra.jdbc.migration.AbstractV2_1_03CheckpointsMigration;
import io.kestra.repository.postgres.PostgresRepositoryEnabled;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * OSS PostgreSQL checkpoints table creation migration.
 */
@Singleton
@PostgresRepositoryEnabled
public class V2_1_03CheckpointsMigration extends AbstractV2_1_03CheckpointsMigration {

    private final DataSource dataSource;

    @Inject
    public V2_1_03CheckpointsMigration(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    protected DataSource dataSource() {
        return dataSource;
    }

    @Override
    public List<String> sqlResources() {
        return List.of("/migrations/2.1.03-checkpoints-postgres.sql");
    }
}
