package io.kestra.cli.commands.flows.namespaces;

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
import static org.hamcrest.core.Is.is;

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
                "io.kestra.cli",
                directory.getPath(),

            };
            PicocliRunner.call(FlowNamespaceUpdateCommand.class, ctx, args);

            assertThat(out.toString(), containsString("3 flow(s)"));
        }
    }

    @Test
    void invalid()  {
        URL directory = FlowNamespaceUpdateCommandTest.class.getClassLoader().getResource("invalids");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setErr(new PrintStream(out));

        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {

            EmbeddedServer embeddedServer = ctx.getBean(EmbeddedServer.class);
            embeddedServer.start();

            String[] args = {
                "--server",
                embeddedServer.getURL().toString(),
                "--user",
                "myuser:pass:word",
                "io.kestra.tests",
                directory.getPath(),

            };
            Integer call = PicocliRunner.call(FlowNamespaceUpdateCommand.class, ctx, args);

            assertThat(call, is(1));
            assertThat(out.toString(), containsString("Unable to parse flows"));
            assertThat(out.toString(), containsString("must not be empty"));
        }
    }

    @Test
    void runNoDelete()  {
        URL directory = FlowNamespaceUpdateCommandTest.class.getClassLoader().getResource("flows");
        URL subDirectory = FlowNamespaceUpdateCommandTest.class.getClassLoader().getResource("flows/flowsSubFolder");

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
                "io.kestra.cli",
                directory.getPath(),

            };
            PicocliRunner.call(FlowNamespaceUpdateCommand.class, ctx, args);

            assertThat(out.toString(), containsString("3 flow(s)"));

            String[] newArgs = {
                "--server",
                embeddedServer.getURL().toString(),
                "--user",
                "myuser:pass:word",
                "io.kestra.cli",
                subDirectory.getPath(),
                "--no-delete"
            };
            PicocliRunner.call(FlowNamespaceUpdateCommand.class, ctx, newArgs);

            assertThat(out.toString(), containsString("1 flow(s)"));
        }
    }
}
