package io.kestra.repository.mysql.migration;

import java.util.List;

import javax.sql.DataSource;

import io.kestra.jdbc.migration.AbstractSQLMigrationScript;
import io.kestra.repository.mysql.MysqlRepositoryEnabled;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * OSS MySQL migration adding the {@code PAUSING} value to {@code executions.state_current}.
 *
 * <p>
 * See {@code /migrations/2.1.03-pausing-state-h2.sql} for why the value exists.
 */
@Singleton
@MysqlRepositoryEnabled
public class V2_1_03PausingStateMigration extends AbstractSQLMigrationScript {

    private static final String SCRIPT_ID = "2.1.03-pausing-state";

    private final DataSource dataSource;

    @Inject
    public V2_1_03PausingStateMigration(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String scriptId() {
        return SCRIPT_ID;
    }

    @Override
    public String description() {
        return "OSS MySQL: add the PAUSING value to executions.state_current";
    }

    @Override
    protected DataSource dataSource() {
        return dataSource;
    }

    @Override
    public List<String> sqlResources() {
        return List.of("/migrations/2.1.03-pausing-state-mysql.sql");
    }
}
