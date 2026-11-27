package io.kestra.cli;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.kestra.core.models.ServerType;
import io.kestra.core.plugins.ExternalPluginsRegistrar;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KestraTest {
    @Test
    void testHelp() {
        // No arg will print help
        var noArgs = ConsoleCapture.run(() -> Kestra.runCli(new String[0]));
        assertThat(noArgs.value()).isZero();
        assertThat(noArgs.out()).contains("kestra");

        // Explicit help command
        var help = ConsoleCapture.run(() -> Kestra.runCli(new String[] { "--help" }));
        assertThat(help.value()).isZero();
        assertThat(help.out()).contains("kestra");
    }

    @ParameterizedTest
    @ValueSource(strings = { "standalone", "executor", "indexer", "scheduler", "webserver", "worker", "controller", "local" })
    void testServerCommandHelp(String serverType) {
        final String[] args = new String[] { "server", serverType, "--help" };

        try (ApplicationContext ctx = Kestra.applicationContext(Kestra.class, new String[] { Environment.CLI }, args)) {
            assertTrue(ctx.getProperty("kestra.server-type", ServerType.class).isEmpty());
        }

        var result = ConsoleCapture.run(() -> Kestra.runCli(args));

        assertThat(result.value()).isZero();
        assertThat(result.out()).contains("Usage: kestra server " + serverType);
    }

    @Test
    void shouldPushPluginsPathWhenCommandLoadsExternalPlugins() {
        String[] args = { "server", "standalone", "--plugins", "/opt/kestra/plugins" };

        try (ApplicationContext ctx = Kestra.applicationContext(Kestra.class, new String[] { Environment.CLI }, args)) {
            ctx.getEnvironment().start();

            assertThat(ctx.getProperty(ExternalPluginsRegistrar.PLUGINS_PATH_PROPERTY, String.class)).contains("/opt/kestra/plugins");
        }
    }

    @Test
    void shouldNotPushPluginsPathWhenCommandSkipsExternalPlugins() {
        String[] args = { "plugins", "install", "--plugins", "/opt/kestra/plugins" };

        try (ApplicationContext ctx = Kestra.applicationContext(Kestra.class, new String[] { Environment.CLI }, args)) {
            ctx.getEnvironment().start();

            assertThat(ctx.getProperty(ExternalPluginsRegistrar.PLUGINS_PATH_PROPERTY, String.class)).isEmpty();
        }
    }

    @Test
    void configBeforeSubcommandIsLoaded() throws Exception {
        // Regression test for: --config placed before the subcommand name was silently
        // dropped by continueOnParsingErrors (introduced in v1.2.0), causing the config
        // file to be ignored and startup to fail with NoSuchBeanException on EE builds.
        // Fix: Kestra.recoverConfigOption() scans raw args and injects the config path into
        // the leaf command instance after continueOnParsingErrors swallows the option.
        Path configFile = Files.createTempFile("kestra-test-", ".yml");
        try {
            Files.writeString(configFile, "kestra:\n  test:\n    marker: config-loaded\n");

            // --config BEFORE "plugins" — this is the position that previously failed.
            String[] args = { "--config", configFile.toString(), "plugins", "list" };

            // Access the private getCommandLine() via reflection — keeps it private in
            // production code while still letting us verify recoverConfigOption() works.
            // We deliberately avoid creating an ApplicationContext here because doing so
            // has global side effects in the test JVM (datasource / gRPC initialization)
            // that break subsequent tests.
            Method getCommandLine = Kestra.class.getDeclaredMethod("getCommandLine", Class.class, String[].class);
            getCommandLine.setAccessible(true);
            CommandLine leafCmd = (CommandLine) getCommandLine.invoke(null, Kestra.class, args);

            Object userObject = leafCmd.getCommandSpec().userObject();
            assertThat(userObject).isInstanceOf(AbstractCommand.class);
            AbstractCommand abstractCmd = (AbstractCommand) userObject;
            assertThat(abstractCmd.propertiesFromConfig())
                .containsEntry("kestra.test.marker", "config-loaded");
        } finally {
            Files.deleteIfExists(configFile);
        }
    }

    @Test
    void missingRequiredParamsPrintHelpInsteadOfException() {
        final String[] argsWithMissingParams = new String[] { "flow", "delete" };

        var result = ConsoleCapture.run(() -> Kestra.runCli(argsWithMissingParams));

        assertThat(result.value()).isEqualTo(2);
        assertThat(result.err()).contains("Missing required parameters: ");
        assertThat(result.err()).contains("Usage: kestra flow delete ");
        assertThat(result.err()).doesNotContain("MissingParameterException: ");
    }
}
