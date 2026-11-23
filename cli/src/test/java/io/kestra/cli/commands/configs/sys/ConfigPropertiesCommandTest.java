package io.kestra.cli.commands.configs.sys;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import io.kestra.cli.ConsoleCapture;

import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.catchThrowable;

class ConfigPropertiesCommandTest {
    @Test
    void run() {
        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {
            String out = ConsoleCapture.run(() -> PicocliRunner.call(ConfigPropertiesCommand.class, ctx)).out();

            assertThat(out).contains("activeEnvironments:");
            assertThat(out).contains("- test");
        }
    }

    @Test
    void shouldOutputCustomEnvironment() {
        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, "custom-env")) {
            String out = ConsoleCapture.run(() -> PicocliRunner.call(ConfigPropertiesCommand.class, ctx)).out();

            assertThat(out).contains("activeEnvironments:");
            assertThat(out).contains("- custom-env");
        }
    }

    @Test
    void shouldReturnZeroOnSuccess() {
        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {
            ConfigPropertiesCommand cmd = ctx.createBean(ConfigPropertiesCommand.class);

            assertThat(ConsoleCapture.run(cmd::call).value()).isZero();
        }
    }

    @Test
    void shouldOutputValidYaml() {
        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {
            String out = ConsoleCapture.run(() -> PicocliRunner.call(ConfigPropertiesCommand.class, ctx)).out();

            // Log lines (which start with a HH:mm:ss.ms timestamp) may be interleaved with the YAML output.
            String yamlOnly = Arrays.stream(out.split("\n"))
                .filter(line -> !line.matches("^\\d{2}:\\d{2}:\\d{2}\\.\\d+.*"))
                .collect(Collectors.joining("\n"));
            Yaml yaml = new Yaml();
            Throwable thrown = catchThrowable(() ->
            {
                Map<?, ?> parsed = yaml.load(yamlOnly);
                assertThat(parsed).isInstanceOf(Map.class);
            });
            assertThat(thrown).isNull();
        }
    }
}