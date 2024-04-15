package io.kestra.cli.commands.flows;

import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.StringContains.containsString;

class FlowValidateCommandTest {
    @Test
    void run() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));

        try (ApplicationContext ctx = ApplicationContext.builder().deduceEnvironment(false).start()) {
            String[] args = {
                "--local",
                "src/test/resources/helper/flow.yaml"
            };
            Integer call = PicocliRunner.call(FlowValidateCommand.class, ctx, args);

            assertThat(call, is(0));
            assertThat(out.toString(), containsString("✓ - io.kestra.cli / include"));
        }
    }

    @Test
    void warning() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));

        try (ApplicationContext ctx = ApplicationContext.builder().deduceEnvironment(false).start()) {
            String[] args = {
                "--local",
                "src/test/resources/warning/flow-with-warning.yaml"
            };
            Integer call = PicocliRunner.call(FlowValidateCommand.class, ctx, args);

            assertThat(call, is(0));
            assertThat(out.toString(), containsString("tasks[0] is deprecated"));
            assertThat(out.toString(), containsString("The system namespace is reserved for background workflows"));
        }
    }
}