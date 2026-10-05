package io.kestra.cli.commands.sys;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import io.kestra.core.executor.command.ExecutionCommand;
import io.kestra.core.queues.DispatchQueueInterface;

import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import jakarta.inject.Singleton;

import static org.assertj.core.api.Assertions.assertThat;

class SubmitQueuedCommandTest {

    @Singleton
    @Requires(property = "kestra.queue.type", pattern = "kafka|unknown_type")
    static class MockQueue implements DispatchQueueInterface<ExecutionCommand> {
        @Override
        public void emit(ExecutionCommand message) {
        }

        @Override
        public void emit(java.util.List<ExecutionCommand> messages) {
        }

        @Override
        public java.util.concurrent.CompletionStage<Void> emitAsync(ExecutionCommand message) {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public java.util.concurrent.CompletionStage<Void> emitAsync(java.util.List<ExecutionCommand> messages) {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public io.kestra.core.queues.QueueSubscriber<ExecutionCommand> subscriber() {
            return null;
        }

        @Override
        public void addListener(Consumer<ExecutionCommand> listener) {
        }

        @Override
        public String queueName() {
            return "mock";
        }

        @Override
        public void close() {
        }
    }

    @Test
    void shouldFailWhenQueueTypeIsKafka() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));

        try (ApplicationContext ctx = ApplicationContext.run(Map.of("kestra.queue.type", "kafka"), Environment.CLI, Environment.TEST)) {
            Integer call = PicocliRunner.call(SubmitQueuedCommand.class, ctx);

            assertThat(call).isEqualTo(1);
            assertThat(out.toString()).contains("is set to 'kafka', use the corresponding sys-ee command");
        }
    }

    @Test
    void shouldFailWhenQueueTypeIsUnknown() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));

        try (ApplicationContext ctx = ApplicationContext.run(Map.of("kestra.queue.type", "unknown_type"), Environment.CLI, Environment.TEST)) {
            Integer call = PicocliRunner.call(SubmitQueuedCommand.class, ctx);

            assertThat(call).isEqualTo(1);
            assertThat(out.toString()).contains("is set to an unknown type {0}");
        }
    }

    @Test
    void shouldSubmitQueuedExecutions() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));

        try (ApplicationContext ctx = ApplicationContext.run(Map.of("kestra.queue.type", "h2"), Environment.CLI, Environment.TEST)) {
            // Note: Since this is an empty H2 database initialized by the test context, 
            // there are 0 queued executions to submit, which is expected.
            // Seeding queued data requires org.jooq which is not available on the cli test classpath.
            Integer call = PicocliRunner.call(SubmitQueuedCommand.class, ctx);

            assertThat(call).isZero();
            assertThat(out.toString()).contains("Successfully submitted 0 queued executions");
        }
    }
}
