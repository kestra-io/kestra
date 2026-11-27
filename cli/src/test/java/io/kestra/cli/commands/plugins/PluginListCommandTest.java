package io.kestra.cli.commands.plugins;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import io.kestra.cli.ConsoleCapture;
import io.kestra.core.PluginTemplateJar;

import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

class PluginListCommandTest {

    @Test
    void shouldListPluginsInstalledLocally() throws IOException {
        Path pluginsPath = Files.createTempDirectory(PluginListCommandTest.class.getSimpleName());
        pluginsPath.toFile().deleteOnExit();

        PluginTemplateJar.copyTo(pluginsPath);

        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {
            String[] args = { "--plugins", pluginsPath.toAbsolutePath().toString() };
            String out = ConsoleCapture.run(() -> PicocliRunner.call(PluginListCommand.class, ctx, args)).out();

            assertThat(out).contains("io.kestra.plugin.templates.Example");
        }
    }
}
