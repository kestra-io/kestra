package io.kestra.cli.commands.sys;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.core.migration.MigrationRunnerInterface;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;

import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.runtime.server.EmbeddedServer;

import static org.assertj.core.api.Assertions.assertThat;

class ReindexCommandTest {
    @Test
    void reindexFlow() throws Exception {
        URL directory = ReindexCommandTest.class.getClassLoader().getResource("flows/same");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));

        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {
            ctx.getBean(MigrationRunnerInterface.class).runAlways();

            EmbeddedServer embeddedServer = ctx.getBean(EmbeddedServer.class);
            embeddedServer.start();

            // load the flows
            LocalFlowRepositoryLoader flowRepositoryLoader = ctx.getBean(LocalFlowRepositoryLoader.class);
            flowRepositoryLoader.load(directory);

            // then we reindex them
            String[] reindexArgs = {
                "--type",
                "flow",
            };
            Integer call = PicocliRunner.call(ReindexCommand.class, ctx, reindexArgs);
            assertThat(call).isZero();
            // in local it reindex 3 flows and in CI 4 for an unknown reason
            assertThat(out.toString()).contains("Successfully reindex");
        } catch (IOException | URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void shouldFailWithAClearMessageWhenTypeIsMissing() {
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        System.setErr(new PrintStream(err));

        int exitCode = PicocliRunner.execute(ReindexCommand.class, List.of(Environment.TEST), new String[0]);

        assertThat(exitCode).isNotZero();
        assertThat(err.toString()).contains("Missing required option").contains("--type");
    }

    @Test
    void shouldFailWithAClearMessageWhenTypeIsUnsupported() {
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        System.setErr(new PrintStream(err));

        int exitCode = PicocliRunner.execute(ReindexCommand.class, List.of(Environment.TEST), new String[] { "--type", "execution" });

        assertThat(exitCode).isNotZero();
        assertThat(err.toString()).contains("Unsupported enum value 'execution'").contains("FLOW");
    }
}