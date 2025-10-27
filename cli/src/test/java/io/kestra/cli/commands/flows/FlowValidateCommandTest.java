package io.kestra.cli.commands.flows;

import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.assertj.core.api.Assertions.assertThat;

class FlowValidateCommandTest {
    @Test
    void run() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));

        try (ApplicationContext ctx = ApplicationContext.builder().deduceEnvironment(false).start()) {
            String[] args = {
                "--local",
                "src/test/resources/helper/include.yaml"
            };
            Integer call = PicocliRunner.call(FlowValidateCommand.class, ctx, args);

            assertThat(call).isZero();
            assertThat(out.toString()).contains("✓ - io.kestra.cli / include");
        }
    }

    @Test
     // github action kestra-io/validate-action requires being able to validate Flows from OSS CLI against a remote EE instance
    void runForEEInstance() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));

        try (ApplicationContext ctx = ApplicationContext.builder().deduceEnvironment(false).start()) {
            String[] args = {
                "--tenant",
                "some-ee-tenant",
                "--local",
                "src/test/resources/helper/include.yaml"
            };
            Integer call = PicocliRunner.call(FlowValidateCommand.class, ctx, args);

            assertThat(call).isZero();
            assertThat(out.toString()).contains("✓ - io.kestra.cli / include");
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

            assertThat(call).isZero();
            assertThat(out.toString()).contains("✓ - system / warning");
            assertThat(out.toString()).contains("⚠ - tasks[0] is deprecated");
            assertThat(out.toString()).contains("ℹ - io.kestra.core.tasks.log.Log is replaced by io.kestra.plugin.core.log.Log");
        }
    }
}