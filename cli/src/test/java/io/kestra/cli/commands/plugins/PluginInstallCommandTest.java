package io.kestra.cli.commands.plugins;

import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PluginInstallCommandTest {

    @Test
    void shouldInstallPluginLocallyGivenFixedVersion() throws IOException {
        Path pluginsPath = Files.createTempDirectory(PluginInstallCommandTest.class.getSimpleName());
        pluginsPath.toFile().deleteOnExit();

        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {
            String[] args = {"--plugins", pluginsPath.toAbsolutePath().toString(), "io.kestra.plugin:plugin-notifications:0.6.0"};
            PicocliRunner.call(PluginInstallCommand.class, ctx, args);

            List<Path> files = Files.list(pluginsPath).toList();

            assertThat(files.size()).isEqualTo(1);
            assertThat(files.getFirst().getFileName().toString()).isEqualTo("io_kestra_plugin__plugin-notifications__0_6_0.jar");
        }
    }

    @Test
    void shouldInstallPluginLocallyGivenLatestVersion() throws IOException {
        Path pluginsPath = Files.createTempDirectory(PluginInstallCommandTest.class.getSimpleName());
        pluginsPath.toFile().deleteOnExit();

        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {
            String[] args = {"--plugins", pluginsPath.toAbsolutePath().toString(), "io.kestra.plugin:plugin-notifications:LATEST"};
            PicocliRunner.call(PluginInstallCommand.class, ctx, args);

            List<Path> files = Files.list(pluginsPath).toList();

            assertThat(files.size()).isEqualTo(1);
            assertThat(files.getFirst().getFileName().toString()).startsWith("io_kestra_plugin__plugin-notifications__");
            assertThat(files.getFirst().getFileName().toString()).doesNotContain("LATEST");
        }
    }

    @Test
    void shouldInstallPluginLocallyGivenRangeVersion() throws IOException {
        Path pluginsPath = Files.createTempDirectory(PluginInstallCommandTest.class.getSimpleName());
        pluginsPath.toFile().deleteOnExit();

        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {
            // SNAPSHOT are included in the 0.12 range not the 0.13, so to avoid resolving it, we must declare it in the upper excluded bound.
            String[] args = {"--plugins", pluginsPath.toAbsolutePath().toString(), "io.kestra.storage:storage-s3:[0.12,0.13.0-SNAPSHOT)"};
            PicocliRunner.call(PluginInstallCommand.class, ctx, args);

            List<Path> files = Files.list(pluginsPath).toList();

            assertThat(files.size()).isEqualTo(1);
            assertThat(files.getFirst().getFileName().toString()).isEqualTo("io_kestra_storage__storage-s3__0_12_1.jar");
        }
    }
}
