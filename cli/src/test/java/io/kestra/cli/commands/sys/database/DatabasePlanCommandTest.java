package io.kestra.cli.commands.sys.database;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class DatabasePlanCommandTest {
    /**
     * Builds the base properties for an isolated H2 datasource. Flyway is left to the caller to enable/disable
     * so we can exercise both the "pending" and "up to date" states.
     */
    private static Map<String, Object> h2Properties(boolean flywayEnabled) {
        Map<String, Object> properties = new HashMap<>();
        properties.put("datasources.h2.url", "jdbc:h2:mem:plan-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        properties.put("datasources.h2.username", "sa");
        properties.put("datasources.h2.password", "");
        properties.put("datasources.h2.driver-class-name", "org.h2.Driver");
        // postgres/mysql are declared in application.yml but have no datasource here, so they are skipped
        properties.put("flyway.datasources.postgres.enabled", false);
        properties.put("flyway.datasources.mysql.enabled", false);
        properties.put("flyway.datasources.h2.enabled", flywayEnabled);
        return properties;
    }

    @Test
    void shouldListPendingMigrationsOnAFreshDatabase() {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));

        // Flyway disabled: nothing is migrated on startup, so every migration is reported as pending.
        try (ApplicationContext ctx = ApplicationContext.builder()
            .deduceEnvironment(false)
            .environments("test")
            .properties(h2Properties(false))
            .start()) {
            Integer call = PicocliRunner.call(DatabasePlanCommand.class, ctx);

            String output = out.toString();
            assertThat(call).isZero();
            assertThat(output).contains("pending migration(s)");
            assertThat(output).contains("V1_1__initial.sql");
        } finally {
            System.setOut(originalOut);
        }
    }

    @Test
    void shouldPrintSqlWhenRequested() {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));

        try (ApplicationContext ctx = ApplicationContext.builder()
            .deduceEnvironment(false)
            .environments("test")
            .properties(h2Properties(false))
            .start()) {
            Integer call = PicocliRunner.call(DatabasePlanCommand.class, ctx, "--sql");

            String output = out.toString();
            assertThat(call).isZero();
            // the actual SQL of the first migration must be printed, not only its name
            assertThat(output).contains("CREATE TABLE IF NOT EXISTS queues");
        } finally {
            System.setOut(originalOut);
        }
    }

    @Test
    void shouldReportNoPendingMigrationWhenUpToDate() {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));

        // Flyway enabled: migrations are applied on startup, so the plan must report nothing pending.
        try (ApplicationContext ctx = ApplicationContext.builder()
            .deduceEnvironment(false)
            .environments("test")
            .properties(h2Properties(true))
            .start()) {
            Integer call = PicocliRunner.call(DatabasePlanCommand.class, ctx);

            String output = out.toString();
            assertThat(call).isZero();
            assertThat(output).contains("No pending migrations.");
            assertThat(output).contains("Total: 0 pending migration(s).");
        } finally {
            System.setOut(originalOut);
        }
    }
}
