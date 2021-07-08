package io.kestra.jdbc;

import com.zaxxer.hikari.HikariConfig;
import io.micronaut.flyway.FlywayConfigurationProperties;
import io.micronaut.flyway.FlywayMigrator;
import lombok.SneakyThrows;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
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
            .filter(throwPredicate(table ->
                (dslContext.dialect() == SQLDialect.MYSQL && table.getSchema().getName().equals(dataSource.getConnection().getCatalog())) ||
                dslContext.dialect() != SQLDialect.MYSQL
            ))
            .filter(table -> !table.getName().equals("flyway_schema_history"))
            .forEach(t -> dslContext.truncate(t.getName()).execute());
    }

    public void migrate() {
        flywayMigrator.run(config, dataSource);
    }
}
