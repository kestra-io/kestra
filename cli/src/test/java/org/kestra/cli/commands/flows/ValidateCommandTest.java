package org.kestra.cli.commands.flows;

import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URL;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.StringContains.containsString;

class ValidateCommandTest {
    @Test
    void run()  {
        URL directory = ValidateCommandTest.class.getClassLoader().getResource("invalids/empty.yaml");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setErr(new PrintStream(out));

        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {
            String[] args = {
                directory.getPath(),
            };
            Integer call = PicocliRunner.call(ValidateCommand.class, ctx, args);

            assertThat(call, is(1));
            assertThat(out.toString(), containsString("Unable to parse flow"));
            assertThat(out.toString(), containsString("must not be empty"));
        }
    }
}