package io.kestra.cli;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.kestra.core.junit.annotations.FlakyTest;
import io.kestra.core.models.ServerType;
import picocli.CommandLine;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppTest {
    @Test
    void testHelp() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));

        // No arg will print help
        assertThat(App.runCli(new String[0])).isZero();
        assertThat(out.toString()).contains("kestra");

        out.reset();

        // Explicit help command
        assertThat(App.runCli(new String[] { "--help" })).isZero();
        assertThat(out.toString()).contains("kestra");
    }

    @ParameterizedTest
    @ValueSource(strings = { "standalone", "executor", "indexer", "scheduler", "webserver", "worker", "local" })
    void testServerCommandHelp(String serverType) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));

        final String[] args = new String[] { "server", serverType, "--help" };

        try (ApplicationContext ctx = App.applicationContext(App.class, new String[] { Environment.CLI }, args)) {
            assertTrue(ctx.getProperty("kestra.server-type", ServerType.class).isEmpty());
        }

        assertThat(App.runCli(args)).isZero();

        assertThat(out.toString()).startsWith("Usage: kestra server " + serverType);
    }

    @FlakyTest(description = "flaky on CI — release triage 2026-06: order-dependent config-recovery assertion")
    @Test
    void configBeforeSubcommandIsLoaded() throws Exception {
        // Regression test for: --config placed before the subcommand name was silently
        // dropped by continueOnParsingErrors (introduced in v1.2.0), causing the config
        // file to be ignored and startup to fail with NoSuchBeanException on EE builds.
        // Fix: App.recoverConfigOption() scans raw args and injects the config path into
        // the leaf command instance after continueOnParsingErrors swallows the option.
        Path configFile = Files.createTempFile("kestra-test-", ".yml");
        try {
            Files.writeString(configFile, "kestra:\n  test:\n    marker: config-loaded\n");

            // --config BEFORE "plugins" — this is the position that previously failed.
            // plugins list has no required positional args so picocli can parse to the leaf
            // command even when --config is silently dropped by continueOnParsingErrors.
            String[] args = { "--config", configFile.toString(), "plugins", "list" };

            // Access the private getCommandLine() via reflection — keeps it private in
            // production code while still letting us verify recoverConfigOption() works.
            // We deliberately avoid creating an ApplicationContext here because doing so
            // has global side effects in the test JVM (datasource / gRPC initialization)
            // that break subsequent tests.
            Method getCommandLine = App.class.getDeclaredMethod("getCommandLine", Class.class, String[].class);
            getCommandLine.setAccessible(true);
            CommandLine leafCmd = (CommandLine) getCommandLine.invoke(null, App.class, args);

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
    void configLoadedWhenRequiredParamsMissing() throws Exception {
        // Regression test for Pylon #1698: when a subcommand is invoked without its required
        // positional parameters, continueOnParsingErrors detaches the subcommand chain so the
        // leaf resolved to the root App and the config file was never loaded — on EE builds this
        // surfaced as a misleading NoSuchBeanException (TenantRepository) instead of a proper
        // "Missing required parameters" message. Fix: getCommandLine() recovers the real leaf via
        // resolveSubcommandByName() so the config is loaded for the right command.
        Path configFile = Files.createTempFile("kestra-test-", ".yml");
        try {
            Files.writeString(configFile, "kestra:\n  test:\n    marker: config-loaded\n");

            // "flow namespace update" requires the <namespace> and <directory> positionals; they
            // are deliberately omitted here so the parse fails and the leaf would otherwise be App.
            String[] args = { "flow", "namespace", "update", "--config", configFile.toString() };

            Method getCommandLine = App.class.getDeclaredMethod("getCommandLine", Class.class, String[].class);
            getCommandLine.setAccessible(true);
            CommandLine leafCmd = (CommandLine) getCommandLine.invoke(null, App.class, args);

            Object userObject = leafCmd.getCommandSpec().userObject();
            assertThat(userObject)
                .as("leaf should be recovered as the real subcommand, not the root App")
                .isInstanceOf(AbstractCommand.class);
            AbstractCommand abstractCmd = (AbstractCommand) userObject;
            assertThat(abstractCmd.propertiesFromConfig())
                .containsEntry("kestra.test.marker", "config-loaded");
        } finally {
            Files.deleteIfExists(configFile);
        }
    }

    @Test
    void missingRequiredParamsPrintHelpInsteadOfException() {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setErr(new PrintStream(out));

        final String[] argsWithMissingParams = new String[] { "flow", "namespace", "update" };

        assertThat(App.runCli(argsWithMissingParams)).isEqualTo(2);

        assertThat(out.toString()).startsWith("Missing required parameters: ");
        assertThat(out.toString()).contains("Usage: kestra flow namespace update ");
        assertThat(out.toString()).doesNotContain("MissingParameterException: ");
    }
}
