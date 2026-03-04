package io.kestra.jdbc;

import io.micronaut.flyway.FlywayConfigurationProperties;
import io.micronaut.flyway.FlywayMigrator;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.SneakyThrows;
import org.jooq.DSLContext;
import org.jooq.Table;
import org.jooq.impl.DSL;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;

import static io.kestra.core.utils.Rethrow.throwPredicate;

@Singleton
public class JdbcTestUtils {
    @Inject
    protected JooqDSLContextWrapper dslContextWrapper;

    @Inject
    private FlywayMigrator flywayMigrator;

    @Inject
    private FlywayConfigurationProperties config;

    @Inject
    private DataSource dataSource;

    @Inject
    private JdbcTableConfigs tableConfigs;

    List<Table<?>> tables;

    @PostConstruct
    public void setup() {
        dslContextWrapper.transaction((configuration) -> {
            DSLContext dslContext = DSL.using(configuration);

            this.tables = dslContext
                .meta()
                .getTables()
                .stream()
                .filter(throwPredicate(table -> (table.getSchema().getName().equals(Optional.ofNullable(dataSource.getConnection().getSchema()).orElse(dataSource.getConnection().getCatalog())))))
                .filter(table -> tableConfigs.getTableConfigs().stream().anyMatch(conf -> conf.table().equalsIgnoreCase(table.getName())))
                .toList();
        });
    }

    /**
     * This should never be used ideally in OSS as it defeats the concurrent test runs and may drop a table in the middle of another test
     */
    @Deprecated
    @SneakyThrows
    public void drop() {
        dslContextWrapper.transaction((configuration) -> {
            DSLContext dslContext = DSL.using(configuration);

            this.tables.forEach(t -> dslContext.delete(t).execute());
        });
    }

    /**
     * This should never be used ideally in OSS as it defeats the concurrent test runs and may drop a table in the middle of another test
     */
    @Deprecated
    public void migrate() {
        dslContextWrapper.transaction((configuration) -> {
            flywayMigrator.run(config, dataSource);
        });
    }
}
