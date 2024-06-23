package io.kestra.cli.commands.plugins;

import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class PluginInstallCommandTest {

    @Test
    void fixedVersion() throws IOException {
        Path pluginsPath = Files.createTempDirectory(PluginInstallCommandTest.class.getSimpleName());
        pluginsPath.toFile().deleteOnExit();

        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {
            String[] args = {"--plugins", pluginsPath.toAbsolutePath().toString(), "io.kestra.plugin:plugin-notifications:0.6.0"};
            PicocliRunner.call(PluginInstallCommand.class, ctx, args);

            List<Path> files = Files.list(pluginsPath).toList();

            assertThat(files.size(), is(1));
            assertThat(files.getFirst().getFileName().toString(), is("plugin-notifications-0.6.0.jar"));
        }
    }

    @Test
    void latestVersion() throws IOException {
        Path pluginsPath = Files.createTempDirectory(PluginInstallCommandTest.class.getSimpleName());
        pluginsPath.toFile().deleteOnExit();

        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {
            String[] args = {"--plugins", pluginsPath.toAbsolutePath().toString(), "io.kestra.plugin:plugin-notifications:LATEST"};
            PicocliRunner.call(PluginInstallCommand.class, ctx, args);

            List<Path> files = Files.list(pluginsPath).toList();

            assertThat(files.size(), is(1));
            assertThat(files.getFirst().getFileName().toString(), startsWith("plugin-notifications"));
            assertThat(files.getFirst().getFileName().toString(), not(containsString("LATEST")));
        }
    }

    @Test
    void rangeVersion() throws IOException {
        Path pluginsPath = Files.createTempDirectory(PluginInstallCommandTest.class.getSimpleName());
        pluginsPath.toFile().deleteOnExit();

        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {
            // SNAPSHOT are included in the 0.12 range not the 0.13, so to avoid resolving it, we must declare it in the upper excluded bound.
            String[] args = {"--plugins", pluginsPath.toAbsolutePath().toString(), "io.kestra.storage:storage-s3:[0.12,0.13.0-SNAPSHOT)"};
            PicocliRunner.call(PluginInstallCommand.class, ctx, args);

            List<Path> files = Files.list(pluginsPath).toList();

            assertThat(files.size(), is(1));
            assertThat(files.getFirst().getFileName().toString(), is("storage-s3-0.12.1.jar"));
        }
    }
}
