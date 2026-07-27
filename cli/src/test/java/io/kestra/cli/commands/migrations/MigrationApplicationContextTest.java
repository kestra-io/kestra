package io.kestra.cli.commands.migrations;

import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;

import io.kestra.cli.Kestra;
import io.kestra.core.migration.MigrationRunner;
import io.kestra.core.migration.MigrationRunnerInterface;
import io.kestra.core.migration.MigrationStartupRunner;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the minimal {@code ApplicationContext} that {@link Kestra} builds for migration commands:
 * the migration runner and its {@link DataSource} must be available, but the {@code @Context}
 * {@link MigrationStartupRunner} (and, by the same rule, every other conditionally-gated Kestra
 * {@code @Context} bean) must not be registered — so starting the context never auto-applies
 * migrations or touches the DB.
 */
class MigrationApplicationContextTest {

    private static final String[] MIGRATE_RUN = {
        "migrate", "run",
        "-c", Paths.get(System.getProperty("java.io.tmpdir"), "kestra-ctx-test-no-such-config.yml").toString()
    };

    private ApplicationContext migrationContext() {
        ApplicationContext ctx = Kestra.applicationContext(
            Kestra.class,
            new String[] { Environment.CLI, Environment.TEST },
            MIGRATE_RUN
        );
        ctx.start();
        return ctx;
    }

    @Test
    void migrationContext_startsRunnerAndDataSource_butNotTheStartupTrigger() throws Exception {
        try (ApplicationContext ctx = migrationContext()) {
            // The runner and its datasource are resolvable (lazily) in the minimal context.
            assertThat(ctx.containsBean(MigrationRunnerInterface.class)).isTrue();
            assertThat(ctx.containsBean(MigrationRunner.class)).isTrue();
            assertThat(ctx.containsBean(DataSource.class)).isTrue();

            // The @Context startup trigger was filtered out, so nothing auto-migrates on start.
            assertThat(ctx.containsBean(MigrationStartupRunner.class)).isFalse();

            // Migrations are therefore still pending (no auto-run happened).
            assertThat(ctx.getBean(MigrationRunner.class).pendingScripts()).isNotEmpty();
        }
    }

    @Test
    void migrationContext_doesNotTouchTheDatabaseOnStartup() throws Exception {
        try (ApplicationContext ctx = migrationContext()) {
            DataSource dataSource = unwrappedDataSource(ctx);

            // Empirical invariant: right after start(), no eager bean has hit the database, so the
            // schema is empty. Any repository/queue/startup @Context bean that slipped through the
            // filter would have bootstrapped tables here.
            assertThat(publicTables(dataSource)).isEmpty();

            // Applying migrations explicitly then creates the schema, including the history table.
            ctx.getBean(MigrationRunnerInterface.class).runAlways();
            assertThat(publicTables(dataSource))
                .isNotEmpty()
                .anyMatch("kestra_migration_history"::equalsIgnoreCase);
        }
    }

    /**
     * The injected {@link DataSource} is Micronaut Data's contextual proxy, whose connections
     * require an active {@code @Connectable} scope. Unwrap it via {@code io.micronaut.jdbc.DataSourceResolver}
     * — the same class {@code JdbcMigrationHistoryStore} uses — so the test can open a plain
     * connection. Reflection is used because {@code micronaut-jdbc} is not on the cli test
     * <em>compile</em> classpath (only at runtime).
     */
    private static DataSource unwrappedDataSource(ApplicationContext ctx) throws Exception {
        DataSource dataSource = ctx.getBean(DataSource.class);
        Class<?> resolverType = Class.forName("io.micronaut.jdbc.DataSourceResolver");
        return ctx.findBean(resolverType)
            .map(resolver ->
            {
                try {
                    return (DataSource) resolverType.getMethod("resolve", DataSource.class)
                        .invoke(resolver, dataSource);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
            })
            .orElse(dataSource);
    }

    private static List<String> publicTables(DataSource dataSource) throws Exception {
        List<String> tables = new ArrayList<>();
        try (
            Connection connection = dataSource.getConnection();
            Statement statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'PUBLIC'"
            )
        ) {
            while (resultSet.next()) {
                tables.add(resultSet.getString(1));
            }
        }
        return tables;
    }
}
