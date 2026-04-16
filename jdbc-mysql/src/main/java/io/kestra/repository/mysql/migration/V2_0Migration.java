package io.kestra.repository.mysql.migration;

import io.kestra.jdbc.migration.AbstractSQLMigrationScript;
import io.kestra.repository.mysql.MysqlRepositoryEnabled;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import javax.sql.DataSource;

/**
 * OSS MySQL init migration script.
 *
 * <p>Creates the full Kestra OSS schema from scratch on fresh MySQL installations.
 * For databases already migrated by Flyway (Kestra &le; 1.3), this script is skipped
 * automatically by {@link io.kestra.core.migration.MigrationRunner} (schema already exists).
 */
@Singleton
@MysqlRepositoryEnabled
public class V2_0Migration extends AbstractSQLMigrationScript {

    private static final String SCRIPT_ID = "0-init";
    private static final String CHECKSUM = "mysql-init-v2.0";

    private final DataSource dataSource;

    @Inject
    public V2_0Migration(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String scriptId() {
        return SCRIPT_ID;
    }

    @Override
    public String description() {
        return "OSS MySQL init: create full schema from scratch";
    }

    @Override
    public String checksum() {
        return CHECKSUM;
    }

    @Override
    public void migrate() throws Exception {
        executeSqlResource(dataSource, "/migrations/baseline-mysql.sql");
    }
}
