package io.kestra.cli.commands.configs.sys;

import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Map;

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

            String output = out.toString();
            Yaml yaml = new Yaml();
            Throwable thrown = catchThrowable(() -> {
                Map<?, ?> parsed = yaml.load(output);
                assertThat(parsed).isInstanceOf(Map.class);
            });
            assertThat(thrown).isNull();
        }
    }
}