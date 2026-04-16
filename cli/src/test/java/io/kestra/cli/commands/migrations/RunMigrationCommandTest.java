package io.kestra.cli.commands.migrations;

import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.assertj.core.api.Assertions.assertThat;

class RunMigrationCommandTest {

    @Test
    void run_appliesMigrationsAndPrintsSuccess() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(out));

        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {
            Integer call = PicocliRunner.call(RunMigrationCommand.class, ctx);
            assertThat(call).isZero();
        } finally {
            System.setOut(originalOut);
        }

        assertThat(out.toString()).contains("All pending migrations have been applied successfully");
    }
}
