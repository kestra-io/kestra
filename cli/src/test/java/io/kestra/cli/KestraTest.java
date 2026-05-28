package io.kestra.cli;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.kestra.core.models.ServerType;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KestraTest {
    @Test
    void testHelp() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));

        // No arg will print help
        assertThat(Kestra.runCli(new String[0])).isZero();
        assertThat(out.toString()).contains("kestra");

        out.reset();

        // Explicit help command
        assertThat(Kestra.runCli(new String[] { "--help" })).isZero();
        assertThat(out.toString()).contains("kestra");
    }

    @ParameterizedTest
    @ValueSource(strings = { "standalone", "executor", "indexer", "scheduler", "webserver", "worker", "controller", "local" })
    void testServerCommandHelp(String serverType) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));

        final String[] args = new String[] { "server", serverType, "--help" };

        try (ApplicationContext ctx = Kestra.applicationContext(Kestra.class, new String[] { Environment.CLI }, args)) {
            assertTrue(ctx.getProperty("kestra.server-type", ServerType.class).isEmpty());
        }

        assertThat(Kestra.runCli(args)).isZero();

        assertThat(out.toString()).contains("Usage: kestra server " + serverType);
    }

    @Test
    void missingRequiredParamsPrintHelpInsteadOfException() {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setErr(new PrintStream(out));

        final String[] argsWithMissingParams = new String[] { "flow", "delete" };

        assertThat(Kestra.runCli(argsWithMissingParams)).isEqualTo(2);

        assertThat(out.toString()).contains("Missing required parameters: ");
        assertThat(out.toString()).contains("Usage: kestra flow delete ");
        assertThat(out.toString()).doesNotContain("MissingParameterException: ");
    }
}
