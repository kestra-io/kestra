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
import static org.hamcrest.Matchers.not;
import static org.hamcrest.core.StringContains.containsString;
import static org.hamcrest.core.Is.is;

class FlowNamespaceUpdateCommandTest {
    @Test
    void runWithDelete() {
        URL directory = FlowNamespaceUpdateCommandTest.class.getClassLoader().getResource("flows/same");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));
        URL subDirectory = FlowNamespaceUpdateCommandTest.class.getClassLoader().getResource("flows/same/flowsSubFolder");

        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {

            EmbeddedServer embeddedServer = ctx.getBean(EmbeddedServer.class);
            embeddedServer.start();

            String[] args = {
                "--server",
                embeddedServer.getURL().toString(),
                "--user",
                "myuser:pass:word",
                "--delete",
                "io.kestra.cli",
                directory.getPath(),
            };
            PicocliRunner.call(FlowNamespaceUpdateCommand.class, ctx, args);

            assertThat(out.toString(), containsString("namespace 'io.kestra.cli' successfully updated"));
            out.reset();

            args = new String[]{
                "--server",
                embeddedServer.getURL().toString(),
                "--user",
                "myuser:pass:word",
                "--delete",
                "io.kestra.cli",
                subDirectory.getPath(),

            };
            PicocliRunner.call(FlowNamespaceUpdateCommand.class, ctx, args);

            // 2 delete + 1 update
            assertThat(out.toString(), containsString("namespace 'io.kestra.cli' successfully updated"));
        }
    }

    @Test
    void invalid() {
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
    void runNoDelete() {
        URL directory = FlowNamespaceUpdateCommandTest.class.getClassLoader().getResource("flows/same");
        URL subDirectory = FlowNamespaceUpdateCommandTest.class.getClassLoader().getResource("flows/same/flowsSubFolder");

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
            out.reset();

            // no "delete" arg should behave as no-delete
            args = new String[]{
                "--server",
                embeddedServer.getURL().toString(),
                "--user",
                "myuser:pass:word",
                "io.kestra.cli",
                subDirectory.getPath()
            };
            PicocliRunner.call(FlowNamespaceUpdateCommand.class, ctx, args);

            assertThat(out.toString(), containsString("1 flow(s)"));
            out.reset();

            args = new String[]{
                "--server",
                embeddedServer.getURL().toString(),
                "--user",
                "myuser:pass:word",
                "--no-delete",
                "io.kestra.cli",
                subDirectory.getPath()
            };
            PicocliRunner.call(FlowNamespaceUpdateCommand.class, ctx, args);

            assertThat(out.toString(), containsString("1 flow(s)"));
        }
    }

    @Test
    void helper() {
        URL directory = FlowNamespaceUpdateCommandTest.class.getClassLoader().getResource("helper");
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
            Integer call = PicocliRunner.call(FlowNamespaceUpdateCommand.class, ctx, args);

            assertThat(call, is(0));
            assertThat(out.toString(), containsString("1 flow(s)"));
        }
    }

    @Test
    void runOverride() {
        URL directory = FlowNamespaceUpdateCommandTest.class.getClassLoader().getResource("flows");
        URL subDirectory = FlowNamespaceUpdateCommandTest.class.getClassLoader().getResource("flows/same/flowsSubFolder");

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
                "io.kestra.override",
                "--override-namespaces",
                directory.getPath(),

            };
            PicocliRunner.call(FlowNamespaceUpdateCommand.class, ctx, args);

            assertThat(out.toString(), containsString("io.kestra.override"));
            assertThat(out.toString(), not(containsString("io.kestra.cli")));

        }
    }
}
