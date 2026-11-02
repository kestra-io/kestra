package io.kestra.repository.h2.migration;

import java.util.List;

import javax.sql.DataSource;

import io.kestra.jdbc.migration.AbstractSQLMigrationScript;
import io.kestra.repository.h2.H2RepositoryEnabled;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * OSS H2 migration renaming the {@code searchable_outputs} table to {@code searchable_data}, and its output columns to data columns.
 */
@Singleton
@H2RepositoryEnabled
public class V2_1_06SearchableDataMigration extends AbstractSQLMigrationScript {

    private static final String SCRIPT_ID = "2.1.06-searchable-data";

    private final DataSource dataSource;

    @Inject
    public V2_1_06SearchableDataMigration(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String scriptId() {
        return SCRIPT_ID;
    }

    @Override
    public String description() {
        return "OSS H2: rename the searchable_outputs table to searchable_data";
    }

    @Override
    protected DataSource dataSource() {
        return dataSource;
    }

    @Override
    public List<String> sqlResources() {
        return List.of("/migrations/2.1.06-searchable-data-h2.sql");
    }
}
