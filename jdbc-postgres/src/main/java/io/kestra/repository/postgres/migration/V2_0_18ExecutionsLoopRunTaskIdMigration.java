package io.kestra.repository.postgres.migration;

import java.util.List;

import javax.sql.DataSource;

import io.kestra.jdbc.migration.AbstractV2_0_18ExecutionsLoopRunTaskIdMigration;
import io.kestra.repository.postgres.PostgresRepositoryEnabled;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * OSS PostgreSQL executions loop-run-task-id migration.
 */
@Singleton
@PostgresRepositoryEnabled
public class V2_0_18ExecutionsLoopRunTaskIdMigration extends AbstractV2_0_18ExecutionsLoopRunTaskIdMigration {

    private final DataSource dataSource;

    @Inject
    public V2_0_18ExecutionsLoopRunTaskIdMigration(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    protected DataSource dataSource() {
        return dataSource;
    }

    @Override
    public List<String> sqlResources() {
        return List.of("/migrations/2.0.18-executions-loop-run-task-id-postgres.sql");
    }
}
