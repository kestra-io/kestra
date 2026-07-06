package io.kestra.cli.commands.sys;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.jupiter.api.Test;

import io.kestra.jdbc.runner.JdbcQueueCleaner;

import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

class PurgeQueueCommandTest {
    @Test
    void shouldReportUnpurgeableQueueTypeAndNotStartServerForMemory() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));

        // The default CLI test environment uses the in-memory queue, which has no 'queues' table to purge.
        // The command must handle it gracefully (exit 0) without booting any server component.
        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {
            Integer call = PicocliRunner.call(PurgeQueueCommand.class, ctx, new String[]{});

            assertThat(call).isZero();
            assertThat(out.toString()).contains("does not use a purgeable");
            // no server component should have been started
            assertThat(ctx.getProperty("kestra.server-type", String.class)).isEmpty();
        }
    }

    @Test
    void shouldPurgeOnJdbcWithoutStartingServer() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));

        // 'purge-queue-h2' sets up a real JDBC (h2) backend WITHOUT a server-type. This is the crux of the
        // feature: the command must resolve JdbcQueueCleaner (whose scheduled sibling JdbcCleaner is gated to
        // EXECUTOR/STANDALONE) and purge the queue even though no executor/worker/scheduler is running.
        // Deletion correctness across dialects is covered by AbstractJdbcCleanerTest (H2/MySQL/Postgres).
        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, "purge-queue-h2")) {
            assertThat(ctx.getProperty("kestra.server-type", String.class)).isEmpty();
            assertThat(ctx.getBean(JdbcQueueCleaner.class)).isNotNull();

            Integer call = PicocliRunner.call(PurgeQueueCommand.class, ctx, new String[]{});

            assertThat(call).isZero();
            assertThat(out.toString()).contains("Successfully purged");
        }
    }
}
