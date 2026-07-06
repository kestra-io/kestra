package io.kestra.repository.h2.migration;

import javax.sql.DataSource;

import io.kestra.core.migration.MigrationScript;
import io.kestra.jdbc.migration.AbstractSQLMigrationScript;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * OSS H2 flows draft migration script.
 *
 * <p>
 * Adds a generated {@code draft} column on the {@code flows} table (derived from the JSON value)
 * so the "latest non-draft revision" query can filter on it directly. Activates only when H2 is
 * the repository backend.
 */
@Singleton
@Requires(property = "kestra.repository.type", pattern = "h2|memory")
public class V2_0_13FlowsDraftMigration extends AbstractSQLMigrationScript {

    private static final String SCRIPT_ID = "2.0.13-flows-draft";

    private final DataSource dataSource;

    @Inject
    public V2_0_13FlowsDraftMigration(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String scriptId() {
        return SCRIPT_ID;
    }

    @Override
    public String description() {
        return "OSS H2 flows draft: add generated draft column and index";
    }

    @Override
    public String checksum() {
        return MigrationScript.checksumOfResources("/migrations/2.0.13-flows-draft-h2.sql");
    }

    @Override
    public void migrate() throws Exception {
        executeSqlResource(dataSource, "/migrations/2.0.13-flows-draft-h2.sql");
    }
}
