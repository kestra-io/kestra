package io.kestra.repository.mysql.migration;

import java.util.List;

import javax.sql.DataSource;

import io.kestra.jdbc.migration.AbstractV2_0_01SchemaMigration;
import io.kestra.repository.mysql.MysqlRepositoryEnabled;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * MySQL Kestra 2.0 schema upgrade migration.
 */
@Singleton
@MysqlRepositoryEnabled
public class V2_0_01SchemaMigration extends AbstractV2_0_01SchemaMigration {

    private final DataSource dataSource;

    @Inject
    public V2_0_01SchemaMigration(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    protected DataSource dataSource() {
        return dataSource;
    }

    @Override
    public List<String> sqlResources() {
        return List.of("/migrations/2.0.01-schema-mysql.sql");
    }
}
