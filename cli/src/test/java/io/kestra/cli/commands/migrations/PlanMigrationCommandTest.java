package io.kestra.cli.commands.migrations;

import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

import io.kestra.cli.ConsoleCapture;
import io.kestra.cli.Kestra;
import io.kestra.core.migration.MigrationRunnerInterface;

import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests {@code migrate plan} against the real minimal migration context built by {@link Kestra}
 * for {@link AbstractMigrationCommand}s. Because that context never registers the
 * {@code @Context MigrationStartupRunner}, no migration is auto-applied on startup, so every script
 * stays pending until it is applied explicitly.
 */
class PlanMigrationCommandTest {

    // Force a non-existent --config so the command does not pick up the developer's ~/.kestra/config.yml;
    // the h2 datasource comes from application-test.yml (TEST environment) instead.
    private static final String[] NO_CONFIG = {
        "-c", Paths.get(System.getProperty("java.io.tmpdir"), "kestra-plan-test-no-such-config.yml").toString()
    };

    private ApplicationContext migrationContext(String... args) {
        String[] full = new String[2 + NO_CONFIG.length + args.length];
        full[0] = "migrate";
        full[1] = "plan";
        System.arraycopy(NO_CONFIG, 0, full, 2, NO_CONFIG.length);
        System.arraycopy(args, 0, full, 2 + NO_CONFIG.length, args.length);
        ApplicationContext ctx = Kestra.applicationContext(
            Kestra.class,
            new String[] { Environment.CLI, Environment.TEST },
            full
        );
        ctx.start();
        return ctx;
    }

    private static String plan(ApplicationContext ctx, String... args) {
        var result = ConsoleCapture.run(() -> PicocliRunner.call(PlanMigrationCommand.class, ctx, args));
        assertThat(result.value()).isZero();
        return result.out();
    }

    @Test
    void plan_listsPendingMigrations_whenNothingApplied() {
        try (ApplicationContext ctx = migrationContext()) {
            String output = plan(ctx);

            assertThat(output).contains("pending migration(s):");
            assertThat(output).contains("0-init");
        }
    }

    @Test
    void plan_printsSql_whenSqlFlagSet() {
        try (ApplicationContext ctx = migrationContext()) {
            String output = plan(ctx, "--sql");

            // the actual SQL of the pending migrations must be printed, not only their ids
            assertThat(output).contains("0-init");
            assertThat(output).containsIgnoringCase("CREATE TABLE");
        }
    }

    @Test
    void plan_reportsNoPending_afterMigrationsApplied() throws Exception {
        try (ApplicationContext ctx = migrationContext()) {
            // Apply every migration explicitly in this context, then plan must report none.
            ctx.getBean(MigrationRunnerInterface.class).runAlways();

            assertThat(plan(ctx)).contains("No pending migrations.");
        }
    }
}
