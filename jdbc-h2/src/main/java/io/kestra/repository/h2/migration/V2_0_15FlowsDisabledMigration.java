package io.kestra.repository.h2.migration;

import javax.sql.DataSource;

import io.kestra.core.migration.MigrationScript;
import io.kestra.jdbc.migration.AbstractSQLMigrationScript;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Adds a generated {@code disabled} column on the {@code flows} table (derived from the JSON value).
 */
@Singleton
@Requires(property = "kestra.repository.type", pattern = "h2|memory")
public class V2_0_15FlowsDisabledMigration extends AbstractSQLMigrationScript {

    private static final String SCRIPT_ID = "2.0.15-flows-disabled";

    private final DataSource dataSource;

    @Inject
    public V2_0_15FlowsDisabledMigration(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String scriptId() {
        return SCRIPT_ID;
    }

    @Override
    public String description() {
        return "OSS H2 flows disabled: add generated disabled column";
    }

    @Override
    public String checksum() {
        return MigrationScript.checksumOfResources("/migrations/2.0.15-flows-disabled-h2.sql");
    }

    @Override
    public void migrate() throws Exception {
        executeSqlResource(dataSource, "/migrations/2.0.15-flows-disabled-h2.sql");
    }
}
