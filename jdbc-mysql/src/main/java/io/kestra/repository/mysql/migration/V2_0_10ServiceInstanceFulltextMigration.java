package io.kestra.repository.mysql.migration;

import java.util.List;

import javax.sql.DataSource;

import io.kestra.jdbc.migration.AbstractSQLMigrationScript;
import io.kestra.repository.mysql.MysqlRepositoryEnabled;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * OSS MySQL migration: adds a FULLTEXT index on {@code (service_id, service_type)} of
 * {@code service_instance} for the query (free-text) filter, mirroring the full-text pattern of the
 * other repositories.
 */
@Singleton
@MysqlRepositoryEnabled
public class V2_0_10ServiceInstanceFulltextMigration extends AbstractSQLMigrationScript {

    private static final String SCRIPT_ID = "2.0.10-service-instance-fulltext";
    private static final String RESOURCE = "/migrations/2.0.10-service-instance-fulltext-mysql.sql";

    private final DataSource dataSource;

    @Inject
    public V2_0_10ServiceInstanceFulltextMigration(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String scriptId() {
        return SCRIPT_ID;
    }

    @Override
    public String description() {
        return "OSS MySQL: add FULLTEXT index on service_instance for the query filter";
    }

    @Override
    public List<String> sqlResources() {
        return List.of(RESOURCE);
    }

    @Override
    protected DataSource dataSource() {
        return dataSource;
    }
}
