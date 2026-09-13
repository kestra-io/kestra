package io.kestra.repository.postgres.migration;

import java.util.List;

import javax.sql.DataSource;

import io.kestra.jdbc.migration.AbstractV2_0Fethr01SchemaMigration;
import io.kestra.repository.postgres.PostgresRepositoryEnabled;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Fethr PostgreSQL schema baseline.
 *
 * <p>
 * PostgreSQL only, matching the fork: secrets, credentials and tables were never implemented for
 * H2 (no H2 Flyway scripts and no H2 repository beans exist for them), so local {@code runLocal}
 * instances have never had these features. Closing that gap is a feature, not part of the port.
 */
@Singleton
@PostgresRepositoryEnabled
public class V2_0Fethr01SchemaMigration extends AbstractV2_0Fethr01SchemaMigration {

    private final DataSource dataSource;

    @Inject
    public V2_0Fethr01SchemaMigration(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    protected DataSource dataSource() {
        return dataSource;
    }

    @Override
    public List<String> sqlResources() {
        return List.of("/migrations/2.0.999999999-fethr-01-schema-postgres.sql");
    }
}
