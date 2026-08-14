package io.kestra.repository.h2.migration;

import io.kestra.jdbc.migration.AbstractV2_0_23AiUsageMigration;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import javax.sql.DataSource;
import java.util.List;

@Singleton
@Requires(property = "kestra.repository.type", pattern = "h2|memory")
public class V2_0_23AiUsageMigration extends AbstractV2_0_23AiUsageMigration {
    private final DataSource dataSource;

    @Inject
    public V2_0_23AiUsageMigration(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    protected DataSource dataSource() {
        return dataSource;
    }

    @Override
    public List<String> sqlResources() {
        return List.of("/migrations/2.0.23-ai-usage-h2.sql");
    }
}
