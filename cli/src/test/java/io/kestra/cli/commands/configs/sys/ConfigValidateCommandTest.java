package io.kestra.cli.commands.configs.sys;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.jupiter.api.Test;

import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigValidateCommandTest {

    @Test
    void shouldReturnZeroAndReportValidWhenConfigurationValid() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));

        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {
            ConfigValidateCommand cmd = ctx.createBean(ConfigValidateCommand.class);
            int result = cmd.call();

            assertThat(result).as("A valid configuration returns exit code 0").isZero();
            assertThat(out.toString())
                .as("The kestra.url check is reported as valid")
                .contains("✓ - kestra.url");
            assertThat(out.toString())
                .as("A summary line confirms the configuration is valid")
                .contains("Configuration is valid.");
        }
    }

    @Test
    void shouldValidateServerRequiredPropertiesWhenServerTypeGiven() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));

        // The CLI test environment defines the queue/repository/storage types, so a webserver
        // server-type validation must pass.
        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {
            Integer result = PicocliRunner.call(ConfigValidateCommand.class, ctx, "--server-type", "webserver");

            assertThat(result).as("A complete server configuration returns exit code 0").isZero();
            assertThat(out.toString())
                .as("The required server properties are reported as valid")
                .contains("✓ - kestra.queue.type");
        }
    }
}
