package io.kestra.cli.commands.migrations;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.jupiter.api.Test;

import io.kestra.core.migration.MigrationRunner;

import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

class PlanMigrationCommandTest {

    @Test
    void plan_listsPendingMigrations_whenNothingApplied() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(out));

        // Skip auto-run so the fresh in-memory H2 keeps every migration pending
        // (this is what PlanMigrationCommand.propertiesOverrides() does in production).
        MigrationRunner.setSkipAutoRun(true);
        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {
            Integer call = PicocliRunner.call(PlanMigrationCommand.class, ctx);
            assertThat(call).isZero();
        } finally {
            MigrationRunner.setSkipAutoRun(false);
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

        MigrationRunner.setSkipAutoRun(true);
        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {
            Integer call = PicocliRunner.call(PlanMigrationCommand.class, ctx, "--sql");
            assertThat(call).isZero();
        } finally {
            MigrationRunner.setSkipAutoRun(false);
            System.setOut(originalOut);
        }

        // the actual SQL of the pending migrations must be printed, not only their ids
        assertThat(out.toString()).contains("0-init");
        assertThat(out.toString()).containsIgnoringCase("CREATE TABLE");
    }

    @Test
    void plan_reportsNoPending_whenDatabaseUpToDate() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(out));

        // Default behaviour: migrations auto-run on startup, so nothing should be pending afterwards.
        MigrationRunner.setSkipAutoRun(false);
        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {
            Integer call = PicocliRunner.call(PlanMigrationCommand.class, ctx);
            assertThat(call).isZero();
        } finally {
            System.setOut(originalOut);
        }

        assertThat(out.toString()).contains("No pending migrations.");
    }
}
