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
import java.util.zip.ZipFile;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.StringContains.containsString;
import static org.hamcrest.core.Is.is;

class TemplateExportCommandTest {
    @Test
    void run() throws IOException {
        URL directory = TemplateExportCommandTest.class.getClassLoader().getResource("templates");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));

        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {

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
            assertThat(out.toString(), containsString("3 template(s)"));

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
            assertThat(file.exists(), is(true));
            ZipFile zipFile = new ZipFile(file);
            assertThat(zipFile.stream().count(), is(3L));

            file.delete();
        }
    }

}