package io.kestra.repository.mysql.migration;

import java.util.List;

import javax.sql.DataSource;

import io.kestra.jdbc.migration.AbstractV2_0_23ExecutionIndexedFieldsMigration;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * OSS MySQL execution indexed fields migration.
 */
@Singleton
@Requires(property = "kestra.repository.type", pattern = "mysql")
public class V2_0_23ExecutionIndexedFieldsMigration extends AbstractV2_0_23ExecutionIndexedFieldsMigration {

    private final DataSource dataSource;

    @Inject
    public V2_0_23ExecutionIndexedFieldsMigration(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    protected DataSource dataSource() {
        return dataSource;
    }

    @Override
    public List<String> sqlResources() {
        return List.of("/migrations/2.0.23-execution-indexed-fields-mysql.sql");
    }
}
