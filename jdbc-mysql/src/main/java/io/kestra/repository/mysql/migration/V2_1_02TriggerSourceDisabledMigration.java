package io.kestra.repository.mysql.migration;

import java.util.List;

import javax.sql.DataSource;

import io.kestra.jdbc.migration.AbstractSQLMigrationScript;
import io.kestra.repository.mysql.MysqlRepositoryEnabled;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * OSS MySQL migration adding the {@code triggers.source_disabled} generated column.
 *
 * <p>
 * See {@code /migrations/2.1.02-trigger-source-disabled-h2.sql} for why the column exists.
 */
@Singleton
@MysqlRepositoryEnabled
public class V2_1_02TriggerSourceDisabledMigration extends AbstractSQLMigrationScript {

    private static final String SCRIPT_ID = "2.1.02-trigger-source-disabled";

    private final DataSource dataSource;

    @Inject
    public V2_1_02TriggerSourceDisabledMigration(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String scriptId() {
        return SCRIPT_ID;
    }

    @Override
    public String description() {
        return "OSS MySQL: add the triggers.source_disabled generated column";
    }

    @Override
    protected DataSource dataSource() {
        return dataSource;
    }

    @Override
    public List<String> sqlResources() {
        return List.of("/migrations/2.1.02-trigger-source-disabled-mysql.sql");
    }
}
