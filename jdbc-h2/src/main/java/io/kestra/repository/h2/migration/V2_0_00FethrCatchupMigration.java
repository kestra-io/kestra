package io.kestra.repository.h2.migration;

import java.util.List;

import javax.sql.DataSource;

import io.kestra.jdbc.migration.AbstractV2_0_00FethrCatchupMigration;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Fethr H2 catch-up migration.
 *
 * <p>
 * The H2 content is derived from upstream's own H2 scripts, not translated from the PostgreSQL
 * ones: upstream numbers the two backends independently past {@code V1_53} (H2's {@code V1_54} is
 * {@code logs_indexes} where PostgreSQL's is {@code logs_metrics_deleted_indices}), and H2 spells
 * the state enum as an inline column type rather than a named type.
 */
@Singleton
@Requires(property = "kestra.repository.type", pattern = "h2|memory")
public class V2_0_00FethrCatchupMigration extends AbstractV2_0_00FethrCatchupMigration {

    private final DataSource dataSource;

    @Inject
    public V2_0_00FethrCatchupMigration(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    protected DataSource dataSource() {
        return dataSource;
    }

    @Override
    public List<String> sqlResources() {
        return List.of("/migrations/2.0.00-fethr-catchup-h2.sql");
    }
}
