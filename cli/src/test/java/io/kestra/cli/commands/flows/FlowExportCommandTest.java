package io.kestra.cli.commands.flows;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.Test;

import io.kestra.core.repositories.LocalFlowRepositoryLoader;

import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.runtime.server.EmbeddedServer;

import static org.assertj.core.api.Assertions.assertThat;

class FlowExportCommandTest {
    @Test
    void run() throws IOException {
        URL directory = FlowExportCommandTest.class.getClassLoader().getResource("flows/same");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));

        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {
            EmbeddedServer embeddedServer = ctx.getBean(EmbeddedServer.class);
            embeddedServer.start();

            // load the flows
            LocalFlowRepositoryLoader flowRepositoryLoader = ctx.getBean(LocalFlowRepositoryLoader.class);
            flowRepositoryLoader.load(directory);

            // then we export them
            String[] exportArgs = {
                "--plugins",
                "/tmp", // pass this arg because it can cause failure
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
            assertThat(file.exists()).isTrue();
            ZipFile zipFile = new ZipFile(file);

            // When launching the test in a suite, there is 4 flows but when launching individually there is only 3
            assertThat(zipFile.stream().count()).isGreaterThanOrEqualTo(3L);

            file.delete();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}