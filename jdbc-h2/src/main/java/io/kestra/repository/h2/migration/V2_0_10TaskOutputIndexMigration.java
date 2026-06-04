package io.kestra.repository.h2.migration;

import io.kestra.core.migration.MigrationScript;
import io.kestra.jdbc.migration.AbstractSQLMigrationScript;
import io.kestra.repository.h2.H2RepositoryEnabled;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import javax.sql.DataSource;

/**
 * H2 task output index migration script.
 *
 * <p>
 * Add an index for taskrunId inside the {@code task_outputs} table
 */
@Singleton
@H2RepositoryEnabled
public class V2_0_10TaskOutputIndexMigration extends AbstractSQLMigrationScript {
    private static final String SCRIPT_ID = "2.0.10-task-outputs-task-run-id-index.sql";

    private final DataSource dataSource;

    @Inject
    public V2_0_10TaskOutputIndexMigration(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String scriptId() {
        return SCRIPT_ID;
    }

    @Override
    public String description() {
        return "H2 task output index migration script: add an index for taskrunId inside the `task_outputs` table.";
    }

    @Override
    public String checksum() {
        return MigrationScript.checksumOfResources("/migrations/" + SCRIPT_ID);
    }

    @Override
    public void migrate() throws Exception {
        executeSqlResource(dataSource, "/migrations/" + SCRIPT_ID);
    }
}
