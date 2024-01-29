package io.kestra.cli.commands.namespaces.files;

import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.runtime.server.EmbeddedServer;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.Test;

import jakarta.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.net.URL;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.core.StringContains.containsString;

class NamespaceFilesUpdateCommandTest {
    @Test
    void runWithToSpecified() {
        URL directory = NamespaceFilesUpdateCommandTest.class.getClassLoader().getResource("namespacefiles/noignore");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));

        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {

            EmbeddedServer embeddedServer = ctx.getBean(EmbeddedServer.class);
            embeddedServer.start();

            String to = "/some/directory";
            String[] args = {
                "--server",
                embeddedServer.getURL().toString(),
                "--user",
                "myuser:pass:word",
                "--delete",
                "io.kestra.cli",
                directory.getPath(),
                to
            };
            PicocliRunner.call(NamespaceFilesUpdateCommand.class, ctx, args);

            assertTransferMessage(out, "2", to);
            assertTransferMessage(out, "1", to);
            assertTransferMessage(out, "flows/flow.yml", to);
            out.reset();
        }
    }

    @Test
    void runWithoutIgnore() throws URISyntaxException {
        URL directory = NamespaceFilesUpdateCommandTest.class.getClassLoader().getResource("namespacefiles/noignore/");
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
                directory.getPath()
            };
            PicocliRunner.call(NamespaceFilesUpdateCommand.class, ctx, args);

            assertTransferMessage(out, "2", null);
            assertTransferMessage(out, "1", null);
            assertTransferMessage(out, "flows/flow.yml", null);
            out.reset();
        }
    }

    @Test
    void runWithIgnore() throws URISyntaxException {
        URL directory = NamespaceFilesUpdateCommandTest.class.getClassLoader().getResource("namespacefiles/ignore/");
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

            assertTransferMessage(out, "2", null);
            assertTransferMessage(out, "1", null);
            assertTransferMessage(out, "flows/flow.yml", null, false);
            out.reset();
        }
    }

    private void assertTransferMessage(ByteArrayOutputStream out, String from, String to) {
        assertTransferMessage(out, from, to, true);
    }

    private void assertTransferMessage(ByteArrayOutputStream out, String relativePath, @Nullable String to, boolean present) {
        to = to == null ? "" : to;
        Matcher<String> matcher = containsString(relativePath + " to " + to + "/" + relativePath);
        assertThat(out.toString(), present ? matcher : not(matcher));
    }
}