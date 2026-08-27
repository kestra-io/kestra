package io.kestra.repository.mysql.migration;

import java.util.List;

import javax.sql.DataSource;

import io.kestra.jdbc.migration.AbstractV2_0_09ExecutionOutputsMigration;
import io.kestra.repository.mysql.MysqlRepositoryEnabled;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * OSS MySQL execution outputs table creation migration.
 */
@Singleton
@MysqlRepositoryEnabled
public class V2_0_09ExecutionOutputsMigration extends AbstractV2_0_09ExecutionOutputsMigration {

    private final DataSource dataSource;

    @Inject
    public V2_0_09ExecutionOutputsMigration(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    protected DataSource dataSource() {
        return dataSource;
    }

    @Override
    public List<String> sqlResources() {
        return List.of("/migrations/2.0.09-execution-outputs-mysql.sql");
    }
}
