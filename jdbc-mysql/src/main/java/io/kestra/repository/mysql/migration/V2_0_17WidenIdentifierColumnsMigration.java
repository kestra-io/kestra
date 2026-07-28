package io.kestra.repository.mysql.migration;

import java.util.List;

import javax.sql.DataSource;

import io.kestra.jdbc.migration.AbstractV2_0_17WidenIdentifierColumnsMigration;
import io.kestra.repository.mysql.MysqlRepositoryEnabled;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * OSS MySQL primary-datasource identifier-column widening migration.
 *
 * <p>
 * Guarded on MySQL: modifying a {@code STORED GENERATED} column rebuilds the table
 * ({@code ALGORITHM=COPY}), so each ALTER widens only when the column is still below its target width.
 */
@Singleton
@MysqlRepositoryEnabled
public class V2_0_17WidenIdentifierColumnsMigration extends AbstractV2_0_17WidenIdentifierColumnsMigration {

    private final DataSource dataSource;

    @Inject
    public V2_0_17WidenIdentifierColumnsMigration(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    protected DataSource dataSource() {
        return dataSource;
    }

    @Override
    public List<String> sqlResources() {
        return List.of("/migrations/2.0.17-widen-identifier-columns-mysql.sql");
    }
}
