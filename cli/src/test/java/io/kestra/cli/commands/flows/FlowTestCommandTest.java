package io.kestra.cli.commands.flows;

import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.assertj.core.api.Assertions.assertThat;

class FlowTestCommandTest {
    @Test
    void run() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));

        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {
            String[] args = {
                "src/test/resources/flows/same/first.yaml"
            };
            Integer call = PicocliRunner.call(FlowTestCommand.class, ctx, args);

            assertThat(call).isZero();
            assertThat(out.toString()).contains("Successfully executed the flow with execution");
            assertThat(out.toString()).contains("SUCCESS");
        }
    }
}