package io.kestra.jdbc;

import io.micronaut.flyway.FlywayConfigurationProperties;
import io.micronaut.flyway.FlywayMigrator;
import lombok.SneakyThrows;
import org.jooq.DSLContext;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.sql.DataSource;

import static io.kestra.core.utils.Rethrow.throwPredicate;

@Singleton
public class JdbcTestUtils {
    @Inject
    private DSLContext dslContext;

    @Inject
    private FlywayMigrator flywayMigrator;

    @Inject
    private FlywayConfigurationProperties config;

    @Inject
    private DataSource dataSource;

    @SneakyThrows
    public void drop() {
        dslContext.meta()
            .getTables()
            .stream()
            .filter(throwPredicate(table -> table.getSchema().getName().equals(dataSource.getConnection().getCatalog())))
            .forEach(t -> dslContext.dropTable(t.getName()).execute());
    }

    public void migrate() {
        flywayMigrator.run(config, dataSource);
    }
}
