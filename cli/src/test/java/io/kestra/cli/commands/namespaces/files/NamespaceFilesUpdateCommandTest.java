package io.kestra.cli.commands.namespaces.files;

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

class NamespaceFilesUpdateCommandTest {
    @Test
    void runWithoutIgnore() {
        URL directory = NamespaceFilesUpdateCommandTest.class.getClassLoader().getResource("namespacefiles/noignore");
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
                "--delete",
                "io.kestra.cli",
                directory.getPath(),
            };
            PicocliRunner.call(NamespaceFilesUpdateCommand.class, ctx, args);

            assertThat(out.toString(), containsString("namespacefiles/noignore/2 to /2"));
            assertThat(out.toString(), containsString("namespacefiles/noignore/flows/flow.yml"));
            assertThat(out.toString(), containsString("namespacefiles/noignore/1 to /1"));
            out.reset();
        }
    }

    @Test
    void runWithIgnore() {
        URL directory = NamespaceFilesUpdateCommandTest.class.getClassLoader().getResource("namespacefiles/ignore");
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
                "--delete",
                "io.kestra.cli",
                directory.getPath(),
            };
            PicocliRunner.call(NamespaceFilesUpdateCommand.class, ctx, args);

            assertThat(out.toString(), containsString("namespacefiles/ignore/2 to /2"));
            assertThat(out.toString(), containsString("namespacefiles/ignore/1 to /1"));
            assertThat(out.toString(), not(containsString("namespacefiles/ignore/flows/flow.yml")));
            out.reset();
        }
    }
}