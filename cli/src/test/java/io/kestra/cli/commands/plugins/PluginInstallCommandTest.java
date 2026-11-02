package io.kestra.cli.commands.plugins;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.RetryingTest;

import io.kestra.core.plugins.PluginArtifact;
import io.kestra.core.plugins.PluginCatalogService;

import io.micronaut.configuration.picocli.MicronautFactory;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

class PluginInstallCommandTest {

    // these tests rely on maven central which can sometimes be unstable, hence the retries
    @RetryingTest(5)
    void shouldInstallPluginLocallyGivenFixedVersion() throws IOException {
        Path pluginsPath = Files.createTempDirectory(PluginInstallCommandTest.class.getSimpleName());
        pluginsPath.toFile().deleteOnExit();

        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {
            String[] args = { "--plugins", pluginsPath.toAbsolutePath().toString(), "io.kestra.plugin:plugin-notifications:0.6.0" };
            callPicocliAndFailIfErrors(PluginInstallCommand.class, ctx, args);

            List<Path> files = Files.list(pluginsPath).toList();

            assertThat(files.stream().map(f -> f.getFileName().toString())).containsExactly("io_kestra_plugin__plugin-notifications__0_6_0.jar");
        }
    }

    @RetryingTest(5)
    void shouldInstallCompatibleVersionLocallyGivenLatestVersion() throws IOException {
        Path pluginsPath = Files.createTempDirectory(PluginInstallCommandTest.class.getSimpleName());
        pluginsPath.toFile().deleteOnExit();

        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {
            PluginArtifact latest = PluginArtifact.fromCoordinates("io.kestra.plugin:plugin-serdes:LATEST");
            String compatibleVersion = ctx.getBean(PluginCatalogService.class).resolveVersions(List.of(latest)).getFirst().version();

            String[] args = { "--plugins", pluginsPath.toAbsolutePath().toString(), latest.toCoordinates() };
            callPicocliAndFailIfErrors(PluginInstallCommand.class, ctx, args);

            List<Path> files = Files.list(pluginsPath).toList();

            assertThat(files.stream().map(f -> f.getFileName().toString()))
                .containsExactly("io_kestra_plugin__plugin-serdes__" + compatibleVersion.replace(".", "_") + ".jar");
        }
    }

    @Test
    void shouldFailWithoutInstallingGivenLatestVersionWhenKestraApiIsUnreachable() throws IOException {
        Path pluginsPath = Files.createTempDirectory(PluginInstallCommandTest.class.getSimpleName());
        pluginsPath.toFile().deleteOnExit();

        try (ApplicationContext ctx = ApplicationContext.run(Map.of("micronaut.http.services.api.url", "http://localhost:1"), Environment.CLI, Environment.TEST)) {
            String[] args = { "--plugins", pluginsPath.toAbsolutePath().toString(), "io.kestra.plugin:plugin-serdes:LATEST" };
            CommandLine commandLine = new CommandLine(PluginInstallCommand.class, new MicronautFactory(ctx));
            commandLine.setErr(new PrintWriter(new ByteArrayOutputStream(), true));

            int exitCode = commandLine.execute(args);

            assertThat(exitCode).isNotZero();
            assertThat(Files.list(pluginsPath)).isEmpty();
        }
    }

    @RetryingTest(5)
    void shouldInstallPluginLocallyGivenRangeVersion() throws IOException {
        Path pluginsPath = Files.createTempDirectory(PluginInstallCommandTest.class.getSimpleName());
        pluginsPath.toFile().deleteOnExit();

        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {
            // SNAPSHOT are included in the 0.12 range not the 0.13, so to avoid resolving it, we must declare it in the upper excluded bound.
            String[] args = { "--plugins", pluginsPath.toAbsolutePath().toString(), "io.kestra.storage:storage-s3:[0.12,0.13.0-SNAPSHOT)" };
            callPicocliAndFailIfErrors(PluginInstallCommand.class, ctx, args);

            List<Path> files = Files.list(pluginsPath).toList();

            assertThat(files.stream().map(f -> f.getFileName().toString())).containsExactly("io_kestra_storage__storage-s3__0_12_1.jar");
        }
    }

    private static void callPicocliAndFailIfErrors(Class clazz, ApplicationContext ctx, String[] args) {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        CommandLine commandLine = new CommandLine(PluginInstallCommand.class, new MicronautFactory(ctx));

        commandLine.setOut(new PrintWriter(stdout, true));
        commandLine.setErr(new PrintWriter(stderr, true));

        int exitCode = commandLine.execute(args);
        if (exitCode != 0) {
            commandLine.getOut();
            String out = stdout.toString(StandardCharsets.UTF_8);
            String err = stderr.toString(StandardCharsets.UTF_8);
            fail("%s returned non-zero exit code for args: '%s', stderr was: %s", clazz.getName(), String.join(" ", args), err);
        }
    }
}
