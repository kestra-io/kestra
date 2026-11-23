package io.kestra.repository.postgres.migration;

import io.kestra.jdbc.migration.AbstractSQLMigrationScript;
import io.kestra.repository.postgres.PostgresRepositoryEnabled;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import javax.sql.DataSource;
import java.util.List;

/**
 * OSS Postgres migration creating the {@code searchable_outputs} table.
 */
@Singleton
@PostgresRepositoryEnabled
public class V2_1_05SearchableOutputsMigration extends AbstractSQLMigrationScript {

    private static final String SCRIPT_ID = "2.1.05-searchable-outputs";

    private final DataSource dataSource;

    @Inject
    public V2_1_05SearchableOutputsMigration(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String scriptId() {
        return SCRIPT_ID;
    }

    @Override
    public String description() {
        return "OSS Postgres: create the searchable_outputs table";
    }

    @Override
    protected DataSource dataSource() {
        return dataSource;
    }

    @Override
    public List<String> sqlResources() {
        return List.of("/migrations/2.1.05-searchable-outputs-postgres.sql");
    }
}
