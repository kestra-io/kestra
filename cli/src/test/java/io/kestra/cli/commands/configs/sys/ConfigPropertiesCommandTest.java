package io.kestra.cli.commands.configs.sys;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.catchThrowable;

class ConfigPropertiesCommandTest {
    @Test
    void run() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));

        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {
            PicocliRunner.call(ConfigPropertiesCommand.class, ctx);

            assertThat(out.toString()).contains("activeEnvironments:");
            assertThat(out.toString()).contains("- test");
        }
    }

    @Test
    void shouldOutputCustomEnvironment() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));

        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, "custom-env")) {
            PicocliRunner.call(ConfigPropertiesCommand.class, ctx);

            assertThat(out.toString()).contains("activeEnvironments:");
            assertThat(out.toString()).contains("- custom-env");
        }
    }

    @Test
    void shouldReturnZeroOnSuccess() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));

        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {
            ConfigPropertiesCommand cmd = ctx.createBean(ConfigPropertiesCommand.class);
            int result = cmd.call();

            assertThat(result).isZero();
        }
    }

    @Test
    void shouldOutputValidYaml() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));

        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {
            PicocliRunner.call(ConfigPropertiesCommand.class, ctx);

            // Migration INFO logs are interleaved with the YAML output on System.out.
            // Filter out log lines (which start with a HH:mm:ss.ms timestamp) before parsing.
            String yamlOnly = Arrays.stream(out.toString().split("\n"))
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