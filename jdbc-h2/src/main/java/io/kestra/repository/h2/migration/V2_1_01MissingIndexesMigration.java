package io.kestra.repository.h2.migration;

import java.util.List;

import javax.sql.DataSource;

import io.kestra.jdbc.migration.AbstractSQLMigrationScript;
import io.kestra.repository.h2.H2RepositoryEnabled;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * OSS H2 migration replacing the {@code flow_topologies} destination+source composite index with a
 * dedicated source index, matching the two indexes MySQL has had since baseline.
 *
 * <p>
 * Without it, the source-side branch of every OR in
 * {@code io.kestra.jdbc.repository.AbstractJdbcFlowTopologyRepository} (including {@code save()},
 * run on every flow save) had no usable index and ran as a full table scan.
 *
 * <p>
 * Counterpart of the MySQL {@code 2.1.01-missing-indexes} migration, which fixes the reverse gap on
 * {@code multipleconditions}.
 */
@Singleton
@H2RepositoryEnabled
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
        return "OSS H2: replace the flow_topologies destination+source index with a dedicated source index";
    }

    @Override
    protected DataSource dataSource() {
        return dataSource;
    }

    @Override
    public List<String> sqlResources() {
        return List.of("/migrations/2.1.01-missing-indexes-h2.sql");
    }
}
