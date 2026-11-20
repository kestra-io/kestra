package io.kestra.cli.commands.configs.sys;

import org.junit.jupiter.api.Test;

import io.kestra.cli.ConsoleCapture;

import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigValidateCommandTest {

    @Test
    void shouldReturnZeroAndReportValidWhenConfigurationValid() throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {
            ConfigValidateCommand cmd = ctx.createBean(ConfigValidateCommand.class);
            var result = ConsoleCapture.run(cmd::call);

            assertThat(result.value()).as("A valid configuration returns exit code 0").isZero();
            assertThat(result.out())
                .as("The kestra.url check is reported as valid")
                .contains("✓ - kestra.url");
            assertThat(result.out())
                .as("A summary line confirms the configuration is valid")
                .contains("Configuration is valid.");
        }
    }

    @Test
    void shouldValidateServerRequiredPropertiesWhenServerTypeGiven() {
        // The CLI test environment defines the queue/repository/storage types, so a webserver
        // server-type validation must pass.
        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {
            var result = ConsoleCapture.run(() -> PicocliRunner.call(ConfigValidateCommand.class, ctx, "--server-type", "webserver"));

            assertThat(result.value()).as("A complete server configuration returns exit code 0").isZero();
            assertThat(result.out())
                .as("The required server properties are reported as valid")
                .contains("✓ - kestra.queue.type");
        }
    }
}
