package io.kestra.repository.h2.migration;

import javax.sql.DataSource;

import io.kestra.core.migration.MigrationScript;
import io.kestra.jdbc.migration.AbstractSQLMigrationScript;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * OSS H2 init migration script.
 *
 * <p>
 * Creates the full Kestra OSS schema from scratch on fresh H2 installations.
 * For databases already migrated by Flyway (Kestra &le; 1.3), this script is skipped
 * automatically by {@link io.kestra.core.migration.MigrationRunner} (schema already exists).
 * <p>
 * Activates only when H2 is the <em>repository</em> backend (not just the queue),
 * using an explicit property check to avoid confusion with {@code @H2RepositoryEnabled}
 * which also matches when H2 is the queue type.
 */
@Singleton
@Requires(property = "kestra.repository.type", pattern = "h2|memory")
public class V2_0Migration extends AbstractSQLMigrationScript {

    private static final String SCRIPT_ID = "0-init";

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
        return "OSS H2 init: create full schema from scratch";
    }

    @Override
    public String checksum() {
        return MigrationScript.checksumOfResources("/migrations/baseline-h2.sql");
    }

    @Override
    public void migrate() throws Exception {
        executeSqlResource(dataSource, "/migrations/baseline-h2.sql");
    }
}
