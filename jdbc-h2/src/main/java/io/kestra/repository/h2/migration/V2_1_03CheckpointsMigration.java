package io.kestra.repository.h2.migration;

import java.util.List;

import javax.sql.DataSource;

import io.kestra.jdbc.migration.AbstractV2_1_03CheckpointsMigration;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * OSS H2 checkpoints table creation migration.
 */
@Singleton
@Requires(property = "kestra.repository.type", pattern = "h2|memory")
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
        return List.of("/migrations/2.1.03-checkpoints-h2.sql");
    }
}
