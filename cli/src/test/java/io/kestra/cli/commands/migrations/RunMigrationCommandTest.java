package io.kestra.cli.commands.migrations;

import org.junit.jupiter.api.Test;

import io.kestra.cli.ConsoleCapture;

import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

class RunMigrationCommandTest {

    @Test
    void run_appliesMigrationsAndPrintsSuccess() {
        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {
            var result = ConsoleCapture.run(() -> PicocliRunner.call(RunMigrationCommand.class, ctx));

            assertThat(result.value()).isZero();
            assertThat(result.out()).contains("All pending migrations have been applied successfully");
        }
    }
}
