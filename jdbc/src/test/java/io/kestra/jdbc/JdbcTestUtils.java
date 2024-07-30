package io.kestra.jdbc;

import io.micronaut.flyway.FlywayConfigurationProperties;
import io.micronaut.flyway.FlywayMigrator;
import lombok.SneakyThrows;
import org.jooq.DSLContext;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jooq.impl.DSL;

import javax.sql.DataSource;

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

    @SneakyThrows
    public void drop() {
        var tableNames = tableConfigs.getTableConfigs().stream().map(conf -> conf.table().toLowerCase()).toList();
        dslContextWrapper.transaction((configuration) -> {
            DSLContext dslContext = DSL.using(configuration);

            dslContext
                .meta()
                .getTables()
                .stream()
                .filter(throwPredicate(table -> (table.getSchema().getName().equals(dataSource.getConnection().getCatalog())) ||
                    table.getSchema().getName().equals("public")  || // for Postgres
                    table.getSchema().getName().equals("dbo") // fo SQLServer
                ))
                .filter(table -> tableNames.contains(table.getName().toLowerCase()))
                .forEach(t -> dslContext.delete(t).execute());
        });
    }

    public void migrate() {
        dslContextWrapper.transaction((configuration) -> {
            flywayMigrator.run(config, dataSource);
        });
    }
}
