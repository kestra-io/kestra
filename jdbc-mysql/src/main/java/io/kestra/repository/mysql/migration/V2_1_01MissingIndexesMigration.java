package io.kestra.repository.mysql.migration;

import java.util.List;

import javax.sql.DataSource;

import io.kestra.jdbc.migration.AbstractSQLMigrationScript;
import io.kestra.repository.mysql.MysqlRepositoryEnabled;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * OSS MySQL migration adding the {@code multipleconditions} indexes that Postgres and H2 have had
 * since baseline.
 *
 * <p>
 * Without them, every {@code FOR UPDATE} lookup and expiry scan in
 * {@code io.kestra.jdbc.runner.AbstractJdbcMultipleConditionStateStore} runs as a full table scan
 * that locks every row it scans for the duration of the enclosing executor transaction.
 *
 * <p>
 * Counterpart of the Postgres/H2 {@code 2.1.01-missing-indexes} migration, which fixes the reverse
 * gap on {@code flow_topologies} (indexed on MySQL since baseline, missing a source-side index on
 * Postgres/H2).
 */
@Singleton
@MysqlRepositoryEnabled
public class V2_1_01MissingIndexesMigration extends AbstractSQLMigrationScript {

    private static final String SCRIPT_ID = "2.1.01-missing-indexes";

    private final DataSource dataSource;

    @Inject
    public V2_1_01MissingIndexesMigration(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String scriptId() {
        return SCRIPT_ID;
    }

    @Override
    public String description() {
        return "OSS MySQL: add missing multipleconditions indexes";
    }

    @Override
    protected DataSource dataSource() {
        return dataSource;
    }

    @Override
    public List<String> sqlResources() {
        return List.of("/migrations/2.1.01-missing-indexes-mysql.sql");
    }
}
