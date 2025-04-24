package io.kestra.cli.commands.flows;

import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;

class FlowDotCommandTest {
    @Test
    void run()  {
        URL directory = TemplateValidateCommandTest.class.getClassLoader().getResource("flows/same/first.yaml");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));

        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {
            String[] args = {
                directory.getPath(),
            };
            Integer call = PicocliRunner.call(FlowDotCommand.class, ctx, args);

            assertThat(call).isZero();
            assertThat(out.toString()).contains("\"root.date\"[shape=box];");
        }
    }
}