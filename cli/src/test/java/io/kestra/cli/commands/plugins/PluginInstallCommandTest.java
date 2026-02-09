package io.kestra.cli.commands.plugins;

import io.micronaut.configuration.picocli.MicronautFactory;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import org.junitpioneer.jupiter.RetryingTest;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

class PluginInstallCommandTest {

    // these tests rely on maven central which can sometimes be unstable, hence the retries
    @RetryingTest(5)
    void shouldInstallPluginLocallyGivenFixedVersion() throws IOException {
        Path pluginsPath = Files.createTempDirectory(PluginInstallCommandTest.class.getSimpleName());
        pluginsPath.toFile().deleteOnExit();

        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {
            String[] args = {"--plugins", pluginsPath.toAbsolutePath().toString(), "io.kestra.plugin:plugin-notifications:0.6.0"};
            callPicocliAndFailIfErrors(PluginInstallCommand.class, ctx, args);

            List<Path> files = Files.list(pluginsPath).toList();

            assertThat(files.stream().map(f -> f.getFileName().toString())).containsExactly("io_kestra_plugin__plugin-notifications__0_6_0.jar");
        }
    }

    @RetryingTest(5)
    void shouldInstallPluginLocallyGivenLatestVersion() throws IOException {
        Path pluginsPath = Files.createTempDirectory(PluginInstallCommandTest.class.getSimpleName());
        pluginsPath.toFile().deleteOnExit();

        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {
            String[] args = {"--plugins", pluginsPath.toAbsolutePath().toString(), "io.kestra.plugin:plugin-notifications:LATEST"};
            callPicocliAndFailIfErrors(PluginInstallCommand.class, ctx, args);

            List<Path> files = Files.list(pluginsPath).toList();

            assertThat(files.size())
                .withFailMessage("expected one file, but got: " + files.stream().map(f->f.getFileName().toString()).collect(Collectors.joining()))
                .isEqualTo(1);
            assertThat(files.getFirst().getFileName().toString()).startsWith("io_kestra_plugin__plugin-notifications__");
            assertThat(files.getFirst().getFileName().toString()).doesNotContain("LATEST");
        }
    }

    @RetryingTest(5)
    void shouldInstallPluginLocallyGivenRangeVersion() throws IOException {
        Path pluginsPath = Files.createTempDirectory(PluginInstallCommandTest.class.getSimpleName());
        pluginsPath.toFile().deleteOnExit();

        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {
            // SNAPSHOT are included in the 0.12 range not the 0.13, so to avoid resolving it, we must declare it in the upper excluded bound.
            String[] args = {"--plugins", pluginsPath.toAbsolutePath().toString(), "io.kestra.storage:storage-s3:[0.12,0.13.0-SNAPSHOT)"};
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
