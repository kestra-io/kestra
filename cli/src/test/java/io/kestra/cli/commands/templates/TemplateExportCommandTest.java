package io.kestra.cli.commands.templates;

import io.kestra.cli.commands.templates.namespaces.TemplateNamespaceUpdateCommand;
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
import java.util.Map;
import java.util.zip.ZipFile;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateExportCommandTest {
    @Test
    void run() throws IOException {
        URL directory = TemplateExportCommandTest.class.getClassLoader().getResource("templates");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));

        try (ApplicationContext ctx = ApplicationContext.run(Map.of("kestra.templates.enabled", "true"), Environment.CLI, Environment.TEST)) {

            EmbeddedServer embeddedServer = ctx.getBean(EmbeddedServer.class);
            embeddedServer.start();

            // we use the update command to add templates to extract
            String[] args = {
                "--server",
                embeddedServer.getURL().toString(),
                "--user",
                "myuser:pass:word",
                "io.kestra.tests",
                directory.getPath(),

            };
            PicocliRunner.call(TemplateNamespaceUpdateCommand.class, ctx, args);
            assertThat(out.toString()).contains("3 template(s)");

            // then we export them
            String[] exportArgs = {
                "--server",
                embeddedServer.getURL().toString(),
                "--user",
                "myuser:pass:word",
                "--namespace",
                "io.kestra.tests",
                "/tmp",
            };
            PicocliRunner.call(TemplateExportCommand.class, ctx, exportArgs);
            File file = new File("/tmp/templates.zip");
            assertThat(file.exists()).isTrue();
            ZipFile zipFile = new ZipFile(file);
            assertThat(zipFile.stream().count()).isEqualTo(3L);

            file.delete();
        }
    }

}