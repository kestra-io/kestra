package io.kestra.repository.postgres.migration;

import java.util.List;

import javax.sql.DataSource;

import io.kestra.jdbc.migration.AbstractV2_0_23ExecutionOutputsMigration;
import io.kestra.repository.postgres.PostgresRepositoryEnabled;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * OSS PostgreSQL execution outputs table creation migration.
 */
@Singleton
@PostgresRepositoryEnabled
public class V2_0_23ExecutionOutputsMigration extends AbstractV2_0_23ExecutionOutputsMigration {

    private final DataSource dataSource;

    @Inject
    public V2_0_23ExecutionOutputsMigration(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    protected DataSource dataSource() {
        return dataSource;
    }

    @Override
    public List<String> sqlResources() {
        return List.of("/migrations/2.0.23-execution-outputs-postgres.sql");
    }
}
