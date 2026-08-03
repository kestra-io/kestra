package io.kestra.repository.postgres.migration;

import java.util.List;

import javax.sql.DataSource;

import io.kestra.jdbc.migration.AbstractV2_0_22ExecutionsNamespaceFlowIdMigration;
import io.kestra.repository.postgres.PostgresRepositoryEnabled;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * OSS PostgreSQL executions namespace/flow_id index merge migration.
 */
@Singleton
@PostgresRepositoryEnabled
public class V2_0_22ExecutionsNamespaceFlowIdMigration extends AbstractV2_0_22ExecutionsNamespaceFlowIdMigration {

    private final DataSource dataSource;

    @Inject
    public V2_0_22ExecutionsNamespaceFlowIdMigration(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    protected DataSource dataSource() {
        return dataSource;
    }

    @Override
    public List<String> sqlResources() {
        return List.of("/migrations/2.0.22-executions-namespace-flow-id-postgres.sql");
    }
}
