package io.kestra.repository.mysql.migration;

import java.util.List;

import javax.sql.DataSource;

import io.kestra.jdbc.migration.AbstractSQLMigrationScript;
import io.kestra.repository.mysql.MysqlRepositoryEnabled;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * OSS Mysql migration renaming the {@code searchable_outputs} table to {@code searchable_data}, and its output columns to data columns.
 */
@Singleton
@MysqlRepositoryEnabled
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
        return "OSS Mysql: rename the searchable_outputs table to searchable_data";
    }

    @Override
    protected DataSource dataSource() {
        return dataSource;
    }

    @Override
    public List<String> sqlResources() {
        return List.of("/migrations/2.1.06-searchable-data-mysql.sql");
    }
}
