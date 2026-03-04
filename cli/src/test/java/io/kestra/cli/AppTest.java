package io.kestra.cli;

import io.kestra.core.models.ServerType;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

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
        assertThat(App.runCli(new String[]{"--help"})).isZero();
        assertThat(out.toString()).contains("kestra");
    }

    @ParameterizedTest
    @ValueSource(strings = {"standalone", "executor", "indexer", "scheduler", "webserver", "worker", "local"})
    void testServerCommandHelp(String serverType) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));

        final String[] args = new String[]{"server", serverType, "--help"};

        try (ApplicationContext ctx = App.applicationContext(App.class, new String [] { Environment.CLI }, args)) {
            assertTrue(ctx.getProperty("kestra.server-type", ServerType.class).isEmpty());
        }

        assertThat(App.runCli(args)).isZero();

        assertThat(out.toString()).startsWith("Usage: kestra server " + serverType);
    }

    @Test
    void missingRequiredParamsPrintHelpInsteadOfException() {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setErr(new PrintStream(out));

        final String[] argsWithMissingParams = new String[]{"flow", "namespace", "update"};

        assertThat(App.runCli(argsWithMissingParams)).isEqualTo(2);

        assertThat(out.toString()).startsWith("Missing required parameters: ");
        assertThat(out.toString()).contains("Usage: kestra flow namespace update ");
        assertThat(out.toString()).doesNotContain("MissingParameterException: ");
    }
}
