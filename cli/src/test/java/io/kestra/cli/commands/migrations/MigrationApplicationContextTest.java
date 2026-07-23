package io.kestra.cli.commands.migrations;

import java.nio.file.Paths;

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
 * {@link MigrationStartupRunner} (and, by the same rule, every other Kestra {@code @Context} bean)
 * must not be registered — so starting the context never auto-applies migrations or touches the DB.
 */
class MigrationApplicationContextTest {

    private static final String[] MIGRATE_RUN = {
        "migrate", "run",
        "-c", Paths.get(System.getProperty("java.io.tmpdir"), "kestra-ctx-test-no-such-config.yml").toString()
    };

    @Test
    void migrationContext_startsRunnerAndDataSource_butNotTheStartupTrigger() {
        try (
            ApplicationContext ctx = Kestra.applicationContext(
                Kestra.class,
                new String[] { Environment.CLI, Environment.TEST },
                MIGRATE_RUN
            )
        ) {
            ctx.start();

            // The runner and its datasource are resolvable (lazily) in the minimal context.
            assertThat(ctx.containsBean(MigrationRunnerInterface.class)).isTrue();
            assertThat(ctx.containsBean(MigrationRunner.class)).isTrue();
            assertThat(ctx.containsBean(DataSource.class)).isTrue();

            // The @Context startup trigger was filtered out, so nothing auto-migrates on start.
            assertThat(ctx.containsBean(MigrationStartupRunner.class)).isFalse();

            // Migrations are therefore still pending (no auto-run happened).
            assertThat(ctx.getBean(MigrationRunner.class).pendingScripts()).isNotEmpty();
        } catch (Exception e) {
            throw new AssertionError("Minimal migration context failed", e);
        }
    }
}
