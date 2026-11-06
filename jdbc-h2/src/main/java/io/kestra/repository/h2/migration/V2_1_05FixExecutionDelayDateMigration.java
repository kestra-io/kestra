package io.kestra.repository.h2.migration;

import java.util.List;

import javax.sql.DataSource;

import io.kestra.jdbc.migration.AbstractSQLMigrationScript;
import io.kestra.repository.h2.H2RepositoryEnabled;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Fixes the {@code executordelayed."date"} generated column so it stores a UTC wall clock
 * independent of the session time zone, as {@code 2.0.01-schema-h2.sql} did for the trigger columns.
 */
@Singleton
@H2RepositoryEnabled
public class V2_1_05FixExecutionDelayDateMigration extends AbstractSQLMigrationScript {

    private static final String SCRIPT_ID = "2.1.05-fix-execution-delay-date";

    private final DataSource dataSource;

    @Inject
    public V2_1_05FixExecutionDelayDateMigration(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String scriptId() {
        return SCRIPT_ID;
    }

    @Override
    public String description() {
        return "OSS H2: fix the executordelayed date generated column to be session-timezone independent";
    }

    @Override
    protected DataSource dataSource() {
        return dataSource;
    }

    @Override
    public List<String> sqlResources() {
        return List.of("/migrations/2.1.05-fix-execution-delay-date-h2.sql");
    }
}
