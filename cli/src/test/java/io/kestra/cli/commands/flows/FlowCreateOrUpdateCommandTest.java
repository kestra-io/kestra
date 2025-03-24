package io.kestra.cli.commands.flows;

import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.RetryingTest;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URL;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.StringContains.containsString;

class FlowCreateOrUpdateCommandTest {
    @RetryingTest(5) // flaky on CI but cannot be reproduced even with 100 repetitions
    void runWithDelete()  {
        URL directory = FlowCreateOrUpdateCommandTest.class.getClassLoader().getResource("flows");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));
        URL subDirectory = FlowCreateOrUpdateCommandTest.class.getClassLoader().getResource("flows/same");

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

            assertThat(out.toString(), containsString("4 flow(s)"));
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
            assertThat(out.toString(), containsString("4 flow(s)"));
        }
    }

    @Test
    void runNoDelete()  {
        URL directory = FlowCreateOrUpdateCommandTest.class.getClassLoader().getResource("flows");
        URL subDirectory = FlowCreateOrUpdateCommandTest.class.getClassLoader().getResource("flows/same/flowsSubFolder");

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
        URL directory = FlowCreateOrUpdateCommandTest.class.getClassLoader().getResource("helper");
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
