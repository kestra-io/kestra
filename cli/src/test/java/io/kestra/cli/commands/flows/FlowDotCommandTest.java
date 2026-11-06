package io.kestra.cli.commands.flows;

import java.net.URL;

import org.junit.jupiter.api.Test;

import io.kestra.cli.ConsoleCapture;

import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

class FlowDotCommandTest {
    @Test
    void run() {
        URL directory = FlowDotCommandTest.class.getClassLoader().getResource("flows/same/first.yaml");

        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {
            String[] args = {
                directory.getPath(),
            };
            var result = ConsoleCapture.run(() -> PicocliRunner.call(FlowDotCommand.class, ctx, args));

            assertThat(result.value()).isZero();
            assertThat(result.out()).contains("\"root.date\"[shape=box];");
        }
    }
}