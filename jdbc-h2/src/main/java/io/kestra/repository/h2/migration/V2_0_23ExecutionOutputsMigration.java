package io.kestra.repository.h2.migration;

import java.util.List;

import javax.sql.DataSource;

import io.kestra.jdbc.migration.AbstractV2_0_23ExecutionOutputsMigration;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * OSS H2 execution outputs table creation migration.
 */
@Singleton
@Requires(property = "kestra.repository.type", pattern = "h2|memory")
public class V2_0_23ExecutionOutputsMigration extends AbstractV2_0_23ExecutionOutputsMigration {

    private final DataSource dataSource;

    @Inject
    public V2_0_23ExecutionOutputsMigration(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    protected DataSource dataSource() {
        return dataSource;
    }

    @Override
    public List<String> sqlResources() {
        return List.of("/migrations/2.0.23-execution-outputs-h2.sql");
    }
}
