package io.kestra.cli;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.kestra.core.models.ServerType;

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

            // --config BEFORE "flow" — this is the position that previously failed
            String[] args = { "--config", configFile.toString(), "flow", "namespace", "update" };

            try (ApplicationContext ctx = App.applicationContext(App.class, new String[] { Environment.CLI }, args)) {
                assertThat(ctx.getProperty("kestra.test.marker", String.class))
                    .hasValue("config-loaded");
            }
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
