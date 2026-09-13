package io.kestra.repository.postgres.migration;

import java.util.List;

import javax.sql.DataSource;

import io.kestra.jdbc.migration.AbstractV2_0_00FethrCatchupMigration;
import io.kestra.repository.postgres.PostgresRepositoryEnabled;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Fethr PostgreSQL catch-up migration.
 */
@Singleton
@PostgresRepositoryEnabled
public class V2_0_00FethrCatchupMigration extends AbstractV2_0_00FethrCatchupMigration {

    private final DataSource dataSource;

    @Inject
    public V2_0_00FethrCatchupMigration(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    protected DataSource dataSource() {
        return dataSource;
    }

    @Override
    public List<String> sqlResources() {
        return List.of("/migrations/2.0.00-fethr-catchup-postgres.sql");
    }
}
