package io.kestra.repository.postgres;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.queues.GenericQueueInterface;
import io.kestra.core.utils.IdUtils;
import io.kestra.jdbc.JdbcTestUtils;
import io.kestra.queue.AbstractDispatchQueueTest;

import io.micronaut.configuration.jdbc.hikari.DatasourceConfiguration;
import io.micronaut.context.annotation.Property;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves {@link PostgresQueueChangeNotifier} actually coalesces: a burst of publishes to the same
 * queue, all within one {@code coalesceInterval}, must not produce one {@code NOTIFY} per publish
 * — the fix for the wake-up-storm half of the throughput regression this mechanism introduced
 * (see PR #17525 benchmark analysis). Uses its own raw {@code LISTEN} connection, independent of
 * {@link PgQueueListener}, so it counts exactly what Postgres delivered rather than what the
 * production wake-up path happened to consume.
 */
@KestraTest(environments = { "test", "queue" }, rebuildContext = true)
@Property(name = "kestra.queue.type", value = "postgres") // the "queue" env sets this to "h2" (still matches @JdbcQueueEnabled generically), which would disable the @PostgresQueueEnabled beans under test
@Property(name = "kestra.queue.postgres.notify.coalesce-interval", value = "0.2s")
@Execution(ExecutionMode.SAME_THREAD)
class PostgresQueueChangeNotifierCoalescingTest {
    @Inject
    private DispatchQueueInterface<AbstractDispatchQueueTest.TestDispatch> dispatchQueue;

    @Inject
    private JdbcTestUtils jdbcTestUtils;

    @Inject
    private DatasourceConfiguration datasourceConfiguration;

    private Connection listenConnection;

    @BeforeEach
    void init() throws SQLException {
        jdbcTestUtils.drop();
        jdbcTestUtils.migrate();

        listenConnection = DriverManager.getConnection(
            datasourceConfiguration.getJdbcUrl(), datasourceConfiguration.getUsername(), datasourceConfiguration.getPassword()
        );
        String channel = PgQueueChannels.channelFor(((GenericQueueInterface<?>) dispatchQueue).queueName());
        try (Statement statement = listenConnection.createStatement()) {
            statement.execute("LISTEN \"" + channel + "\"");
        }
    }

    @AfterEach
    void cleanup() throws SQLException {
        listenConnection.close();
    }

    @Test
    void shouldCoalesceABurstOfPublishesIntoFewNotifications() throws Exception {
        int publishCount = 30;
        for (int i = 0; i < publishCount; i++) {
            dispatchQueue.emit(new AbstractDispatchQueueTest.TestDispatch(IdUtils.create(), i));
        }

        // Drain notifications over a window comfortably longer than the 0.2s coalesceInterval, so
        // both the immediate leading-edge NOTIFY and any trailing coalesced one have time to arrive.
        int received = 0;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        PGConnection pgConnection = (PGConnection) listenConnection;
        while (System.nanoTime() < deadline) {
            PGNotification[] notifications = pgConnection.getNotifications(200);
            if (notifications != null) {
                received += notifications.length;
            }
        }

        assertThat(received)
            .as("a %d-message burst within one coalescing window should not produce one NOTIFY per message", publishCount)
            .isPositive()
            .isLessThan(publishCount);
    }
}
