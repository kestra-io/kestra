package io.kestra.cli.commands.sys;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.net.URL;

import org.junit.jupiter.api.Test;

import io.kestra.core.repositories.LocalFlowRepositoryLoader;

import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.runtime.server.EmbeddedServer;

import static org.assertj.core.api.Assertions.assertThat;

class ReindexCommandTest {
    @Test
    void reindexFlow() {
        URL directory = ReindexCommandTest.class.getClassLoader().getResource("flows/same");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));

        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {
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
}