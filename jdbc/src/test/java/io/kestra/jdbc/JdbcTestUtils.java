package io.kestra.jdbc;

import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.jooq.DSLContext;
import org.jooq.Table;
import org.jooq.impl.DSL;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import lombok.SneakyThrows;

import static io.kestra.core.utils.Rethrow.throwPredicate;

@Context
@Requires(property = "kestra.repository.type", pattern = "mysql|postgres|h2|memory")
public class JdbcTestUtils {
    @Inject
    protected JooqDSLContextWrapper dslContextWrapper;

    @Inject
    private DataSource dataSource;

    @Inject
    private JdbcTableConfigs tableConfigs;

    List<Table<?>> tables;

    @PostConstruct
    @SneakyThrows
    public void setup() {

        dslContextWrapper.transaction((configuration) ->
        {
            DSLContext dslContext = DSL.using(configuration);

            this.tables = dslContext
                .meta()
                .getTables()
                .stream()
                .filter(
                    throwPredicate(table -> (table.getSchema().getName().equals(Optional.ofNullable(dataSource.getConnection().getSchema()).orElse(dataSource.getConnection().getCatalog()))))
                )
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
        dslContextWrapper.transaction((configuration) ->
        {
            DSLContext dslContext = DSL.using(configuration);

            this.tables.forEach(t -> dslContext.delete(t).execute());
        });
    }

    /**
     * No-op. Migrations are now automatically applied at context startup via
     * {@code MigrationRunner.@PostConstruct}.
     */
    @Deprecated
    public void migrate() {
    }
}
