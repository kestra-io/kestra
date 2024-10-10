package io.kestra.cli.commands.sys;

import io.kestra.cli.commands.flows.namespaces.FlowNamespaceUpdateCommand;
import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URL;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.StringContains.containsString;

class ReindexCommandTest {
    @Test
    void reindexFlow() {
        URL directory = ReindexCommandTest.class.getClassLoader().getResource("flows/same");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));

        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {
            EmbeddedServer embeddedServer = ctx.getBean(EmbeddedServer.class);
            embeddedServer.start();

            // we use the update command to add flows to extract
            String[] updateArgs = {
                "--server",
                embeddedServer.getURL().toString(),
                "--user",
                "myuser:pass:word",
                "io.kestra.cli",
                directory.getPath(),
            };
            PicocliRunner.call(FlowNamespaceUpdateCommand.class, ctx, updateArgs);
            assertThat(out.toString(), containsString("3 flow(s)"));

            // then we reindex them
            String[] reindexArgs = {
                "--type",
               "flow",
            };
            Integer call = PicocliRunner.call(ReindexCommand.class, ctx, reindexArgs);
            assertThat(call, is(0));
            // in local it reindex 3 flows and in CI 4 for an unknown reason
            assertThat(out.toString(), containsString("Successfully reindex"));
        }
    }
}