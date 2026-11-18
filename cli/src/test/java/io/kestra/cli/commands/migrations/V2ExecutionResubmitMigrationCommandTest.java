package io.kestra.cli.commands.migrations;

import org.junit.jupiter.api.Test;

import io.kestra.cli.ConsoleCapture;

import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

class V2ExecutionResubmitMigrationCommandTest {

    @Test
    void run_resubmitsExecutionsAndPrintsSuccess() {
        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {
            var result = ConsoleCapture.run(() -> PicocliRunner.call(V2ExecutionResubmitMigrationCommand.class, ctx));

            assertThat(result.value()).isZero();
            assertThat(result.out()).contains("Migration complete");
        }
    }
}
