package io.kestra.repository.postgres.migration;

import java.util.List;

import javax.sql.DataSource;

import io.kestra.jdbc.migration.AbstractV2_0_16WidenMetricsTaskIdMigration;
import io.kestra.repository.postgres.PostgresRepositoryEnabled;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * OSS Postgres metrics task_id widening migration (primary datasource).
 */
@Singleton
@PostgresRepositoryEnabled
public class V2_0_16WidenMetricsTaskIdMigration extends AbstractV2_0_16WidenMetricsTaskIdMigration {

    private final DataSource dataSource;

    @Inject
    public V2_0_16WidenMetricsTaskIdMigration(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    protected DataSource dataSource() {
        return dataSource;
    }

    @Override
    public List<String> sqlResources() {
        return List.of("/migrations/2.0.16-widen-metrics-task-id-postgres.sql");
    }
}
