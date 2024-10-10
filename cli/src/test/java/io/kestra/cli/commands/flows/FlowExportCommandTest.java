package io.kestra.cli.commands.flows;

import io.kestra.cli.commands.flows.namespaces.FlowNamespaceUpdateCommand;
import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.util.zip.ZipFile;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.StringContains.containsString;

class FlowExportCommandTest {
    @Test
    void run() throws IOException {
        URL directory = FlowExportCommandTest.class.getClassLoader().getResource("flows/same");
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

            // then we export them
            String[] exportArgs = {
                "--server",
                embeddedServer.getURL().toString(),
                "--user",
                "myuser:pass:word",
                "--namespace",
                "io.kestra.cli",
                "/tmp",
            };
            PicocliRunner.call(FlowExportCommand.class, ctx, exportArgs);
            File file = new File("/tmp/flows.zip");
            assertThat(file.exists(), is(true));
            ZipFile zipFile = new ZipFile(file);

            // When launching the test in a suite, there is 4 flows but when lauching individualy there is only 3
            assertThat(zipFile.stream().count(), greaterThanOrEqualTo(3L));

            file.delete();
        }
    }
}