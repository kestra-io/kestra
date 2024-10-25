package io.kestra.cli.commands.flows;

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

class FlowUpdatesCommandTest {
    @Test
    void runWithDelete()  {
        URL directory = FlowUpdatesCommandTest.class.getClassLoader().getResource("flows");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));
        URL subDirectory = FlowUpdatesCommandTest.class.getClassLoader().getResource("flows/same");

        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {

            EmbeddedServer embeddedServer = ctx.getBean(EmbeddedServer.class);
            embeddedServer.start();

            String[] args = {
                "--server",
                embeddedServer.getURL().toString(),
                "--user",
                "myuser:pass:word",
                "--delete",
                directory.getPath(),
            };
            PicocliRunner.call(FlowUpdatesCommand.class, ctx, args);

            assertThat(out.toString(), containsString("successfully updated !"));
            out.reset();

            args = new String[]{
                "--server",
                embeddedServer.getURL().toString(),
                "--user",
                "myuser:pass:word",
                "--delete",
                subDirectory.getPath(),

            };
            PicocliRunner.call(FlowUpdatesCommand.class, ctx, args);

            // 2 delete + 1 update
            assertThat(out.toString(), containsString("successfully updated !"));
        }
    }

    @Test
    void runNoDelete()  {
        URL directory = FlowUpdatesCommandTest.class.getClassLoader().getResource("flows");
        URL subDirectory = FlowUpdatesCommandTest.class.getClassLoader().getResource("flows/same/flowsSubFolder");

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
                directory.getPath(),

            };
            PicocliRunner.call(FlowUpdatesCommand.class, ctx, args);

            assertThat(out.toString(), containsString("4 flow(s)"));
            out.reset();

            // no "delete" arg should behave as no-delete
            args = new String[]{
                "--server",
                embeddedServer.getURL().toString(),
                "--user",
                "myuser:pass:word",
                subDirectory.getPath()
            };
            PicocliRunner.call(FlowUpdatesCommand.class, ctx, args);

            assertThat(out.toString(), containsString("1 flow(s)"));
            out.reset();

            args = new String[]{
                "--server",
                embeddedServer.getURL().toString(),
                "--user",
                "myuser:pass:word",
                "--no-delete",
                subDirectory.getPath()
            };
            PicocliRunner.call(FlowUpdatesCommand.class, ctx, args);

            assertThat(out.toString(), containsString("1 flow(s)"));
        }
    }

    @Test
    void helper()  {
        URL directory = FlowUpdatesCommandTest.class.getClassLoader().getResource("helper");
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
                directory.getPath(),

            };
            Integer call = PicocliRunner.call(FlowUpdatesCommand.class, ctx, args);

            assertThat(call, is(0));
            assertThat(out.toString(), containsString("1 flow(s)"));
        }
    }
}
