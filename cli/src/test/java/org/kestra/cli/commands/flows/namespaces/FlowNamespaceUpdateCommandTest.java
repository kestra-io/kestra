package org.kestra.cli.commands.flows.namespaces;

import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URL;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.StringContains.containsString;

class FlowNamespaceUpdateCommandTest {
    @Test
    void run()  {
        URL directory = FlowNamespaceUpdateCommandTest.class.getClassLoader().getResource("flows");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));

        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {

            EmbeddedServer embeddedServer = ctx.getBean(EmbeddedServer.class);
            embeddedServer.start();

            String[] args = {
                "--server",
                embeddedServer.getURL().toString(),
                "--user",
                "myuser:pass:word",
                "org.kestra.cli",
                directory.getPath(),

            };
            PicocliRunner.call(FlowNamespaceUpdateCommand.class, ctx, args);

            assertThat(out.toString(), containsString("2 flow(s)"));
        }
    }
}
