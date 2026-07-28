package io.kestra.cli.commands.migrations;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

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

    @Test
    void plan_listsPendingMigrations_whenNothingApplied() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(out));

        try (ApplicationContext ctx = migrationContext()) {
            Integer call = PicocliRunner.call(PlanMigrationCommand.class, ctx);
            assertThat(call).isZero();
        } finally {
            System.setOut(originalOut);
        }

        assertThat(out.toString()).contains("pending migration(s):");
        assertThat(out.toString()).contains("0-init");
    }

    @Test
    void plan_printsSql_whenSqlFlagSet() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(out));

        try (ApplicationContext ctx = migrationContext()) {
            Integer call = PicocliRunner.call(PlanMigrationCommand.class, ctx, "--sql");
            assertThat(call).isZero();
        } finally {
            System.setOut(originalOut);
        }

        // the actual SQL of the pending migrations must be printed, not only their ids
        assertThat(out.toString()).contains("0-init");
        assertThat(out.toString()).containsIgnoringCase("CREATE TABLE");
    }

    @Test
    void plan_reportsNoPending_afterMigrationsApplied() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(out));

        try (ApplicationContext ctx = migrationContext()) {
            // Apply every migration explicitly in this context, then plan must report none.
            ctx.getBean(MigrationRunnerInterface.class).runAlways();

            Integer call = PicocliRunner.call(PlanMigrationCommand.class, ctx);
            assertThat(call).isZero();
        } finally {
            System.setOut(originalOut);
        }

        assertThat(out.toString()).contains("No pending migrations.");
    }
}
