package io.kestra.repository.h2.migration;

import java.util.List;

import javax.sql.DataSource;

import io.kestra.jdbc.migration.AbstractSQLMigrationScript;
import io.kestra.repository.h2.H2RepositoryEnabled;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * OSS H2 migration adding the {@code triggers.source_disabled} generated column.
 *
 * <p>
 * {@code TriggerState.disabled} holds only the runtime disable, so the trigger-state search filter
 * could no longer see a trigger disabled in its flow definition and reported it as enabled. The
 * definition flag is mirrored into {@code TriggerState.sourceDisabled}, and this column exposes it
 * to the filter.
 */
@Singleton
@H2RepositoryEnabled
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
        return "OSS H2: add the triggers.source_disabled generated column";
    }

    @Override
    protected DataSource dataSource() {
        return dataSource;
    }

    @Override
    public List<String> sqlResources() {
        return List.of("/migrations/2.1.02-trigger-source-disabled-h2.sql");
    }
}
