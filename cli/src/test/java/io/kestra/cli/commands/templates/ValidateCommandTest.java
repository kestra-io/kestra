package io.kestra.cli.commands.templates;

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

public class ValidateCommandTest {
    @Test
    void runLocal()  {
        URL directory = ValidateCommandTest.class.getClassLoader().getResource("invalidsTemplates/template.yml");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setErr(new PrintStream(out));

        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {
            String[] args = {
                "--local",
                directory.getPath()
            };
            Integer call = PicocliRunner.call(ValidateCommand.class, ctx, args);

            assertThat(call, is(1));
            assertThat(out.toString(), containsString("Unable to parse template"));
            assertThat(out.toString(), containsString("must not be empty"));
        }
    }

    @Test
    void runServer()  {
        URL directory = ValidateCommandTest.class.getClassLoader().getResource("invalidsTemplates/template.yml");
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
                directory.getPath()
            };
            Integer call = PicocliRunner.call(ValidateCommand.class, ctx, args);

            assertThat(call, is(1));
            assertThat(out.toString(), containsString("Unable to parse template"));
            assertThat(out.toString(), containsString("must not be empty"));
        }
    }
}
