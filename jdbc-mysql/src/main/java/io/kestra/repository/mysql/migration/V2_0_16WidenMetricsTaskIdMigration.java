package io.kestra.repository.mysql.migration;

import java.util.List;

import javax.sql.DataSource;

import io.kestra.jdbc.migration.AbstractV2_0_16WidenMetricsTaskIdMigration;
import io.kestra.repository.mysql.MysqlRepositoryEnabled;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * OSS MySQL metrics task_id widening migration (primary datasource).
 *
 * <p>
 * Guarded on MySQL: modifying a {@code STORED GENERATED} column rebuilds the table
 * ({@code ALGORITHM=COPY}), so the SQL widens only when the column is still shorter than 256,
 * skipping the rebuild for installs already at 256 (e.g. arriving from Kestra 1.3.x).
 */
@Singleton
@MysqlRepositoryEnabled
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
        return List.of("/migrations/2.0.16-widen-metrics-task-id-mysql.sql");
    }
}
