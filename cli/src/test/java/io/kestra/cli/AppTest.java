package io.kestra.cli;

import io.kestra.core.models.ServerType;
import io.micronaut.configuration.picocli.MicronautFactory;
import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppTest {
    @Test
    void testHelp() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));

        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {
            PicocliRunner.call(App.class, ctx, "--help");

            assertThat(out.toString(), containsString("kestra"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"standalone", "executor", "indexer", "scheduler", "webserver", "worker", "local"})
    void testServerCommandHelp(String serverType) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));

        final String[] args = new String[]{"server", serverType, "--help"};

        try (ApplicationContext ctx = App.applicationContext(App.class, args)) {
            new CommandLine(App.class, new MicronautFactory(ctx)).execute(args);

            assertTrue(ctx.getProperty("kestra.server-type", ServerType.class).isEmpty());
            assertThat(out.toString(), startsWith("Usage: kestra server " + serverType));
        }
    }
}
