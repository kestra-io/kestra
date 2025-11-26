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
                .filter(throwPredicate(table -> (table.getSchema().getName().equals(Optional.ofNullable(dataSource.getConnection().getSchema()).orElse(dataSource.getConnection().getCatalog())))
                    || table.getSchema().getName().equals("dbo")))
                .filter(table -> tableConfigs.getTableConfigs().stream().anyMatch(conf -> conf.table().equalsIgnoreCase(table.getName())))
                .toList();
        });
    }

    @SneakyThrows
    public void drop() {
        dslContextWrapper.transaction((configuration) -> {
            DSLContext dslContext = DSL.using(configuration);

            this.tables.forEach(t -> dslContext.delete(t).execute());
        });
    }

    public void migrate() {
        dslContextWrapper.transaction((configuration) -> {
            flywayMigrator.run(config, dataSource);
        });
    }
}
