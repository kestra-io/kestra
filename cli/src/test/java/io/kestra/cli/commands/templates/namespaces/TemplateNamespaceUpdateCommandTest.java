package io.kestra.cli.commands.templates.namespaces;

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

class TemplateNamespaceUpdateCommandTest {
    @Test
    void run() {
        URL directory = TemplateNamespaceUpdateCommandTest.class.getClassLoader().getResource("templates");
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
                "io.kestra.tests",
                directory.getPath(),

            };
            PicocliRunner.call(TemplateNamespaceUpdateCommand.class, ctx, args);

            assertThat(out.toString(), containsString("3 template(s)"));
        }
    }

    @Test
    void invalid() {
        URL directory = TemplateNamespaceUpdateCommandTest.class.getClassLoader().getResource("invalidsTemplates");
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
            Integer call = PicocliRunner.call(TemplateNamespaceUpdateCommand.class, ctx, args);

//            assertThat(call, is(1));
            assertThat(out.toString(), containsString("Unable to parse templates"));
            assertThat(out.toString(), containsString("must not be empty"));
        }
    }

    @Test
    void runNoDelete() {
        URL directory = TemplateNamespaceUpdateCommandTest.class.getClassLoader().getResource("templates");
        URL subDirectory = TemplateNamespaceUpdateCommandTest.class.getClassLoader().getResource("templates/templatesSubFolder");

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
                "io.kestra.tests",
                directory.getPath(),

            };
            PicocliRunner.call(TemplateNamespaceUpdateCommand.class, ctx, args);

            assertThat(out.toString(), containsString("3 template(s)"));

            String[] newArgs = {
                "--server",
                embeddedServer.getURL().toString(),
                "--user",
                "myuser:pass:word",
                "io.kestra.tests",
                subDirectory.getPath(),
                "--no-delete"

            };
            PicocliRunner.call(TemplateNamespaceUpdateCommand.class, ctx, newArgs);

            assertThat(out.toString(), containsString("1 template(s)"));
        }
    }
}