package io.kestra.repository.postgres;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
 * Proves {@link PostgresQueueChangeNotifier} floors its effective coalescing interval at the
 * subscriber's own {@code min-poll-interval}, even when {@code coalesce-interval} is configured
 * tighter. Without this floor, a NOTIFY-driven wake-up on a briefly-idle, high-throughput queue
 * can re-poll (and pay a full transaction) more often than plain polling ever would — the residual
 * high-throughput regression this floor fixes.
 * <p>
 * Configures {@code coalesce-interval} (10ms) well below {@code min-poll-interval} (300ms) and
 * publishes continuously, then asserts the total NOTIFY count over the observation window is
 * bounded near what the 300ms floor would produce (a handful), not the ~100+ that the tighter
 * configured 10ms value would produce if it weren't floored. Bounding on total count rather than
 * per-notification arrival gaps (like the sibling {@link PostgresQueueChangeNotifierCoalescingTest})
 * avoids a timing trap: a stalled poll loop (GC pause, CI contention) can return several
 * already-buffered NOTIFYs in one {@code getNotifications} call, making two genuinely
 * floor-spaced signals look like a zero-gap batch — a false failure unrelated to the mechanism
 * under test.
 */
@KestraTest(environments = { "test", "queue" }, rebuildContext = true)
@Property(name = "kestra.queue.type", value = "postgres") // the "queue" env sets this to "h2" (still matches @JdbcQueueEnabled generically), which would disable the @PostgresQueueEnabled beans under test
@Property(name = "kestra.queue.postgres.notify.coalesce-interval", value = "0.01s")
@Property(name = "kestra.jdbc.queues.min-poll-interval", value = "0.3s")
@Execution(ExecutionMode.SAME_THREAD)
class PostgresQueueChangeNotifierFloorTest {
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
    void shouldNotUndercutTheSubscribersPollFloorWhenCoalesceIntervalIsTighter() throws Exception {
        long publishDurationNanos = TimeUnit.MILLISECONDS.toNanos(900);
        long publishDeadline = System.nanoTime() + publishDurationNanos;

        AtomicInteger id = new AtomicInteger();
        Thread publisher = new Thread(() ->
        {
            while (System.nanoTime() < publishDeadline) {
                try {
                    dispatchQueue.emit(new AbstractDispatchQueueTest.TestDispatch(IdUtils.create(), id.incrementAndGet()));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });
        publisher.start();

        int received = 0;
        long collectDeadline = publishDeadline + TimeUnit.MILLISECONDS.toNanos(500);
        PGConnection pgConnection = (PGConnection) listenConnection;
        while (System.nanoTime() < collectDeadline) {
            PGNotification[] notifications = pgConnection.getNotifications(200);
            if (notifications != null) {
                received += notifications.length;
            }
        }
        publisher.join();

        // Observation window is ~1.4s (900ms publishing + 500ms drain). At the 300ms floor that's
        // at most ~5-6 NOTIFYs; at the tighter configured 10ms coalesce-interval it would be 100+.
        // A generous upper bound well below the unfloor value proves the floor is what's actually
        // governing emission cadence, without depending on precise per-notification timing.
        assertThat(received)
            .as("NOTIFY count must reflect the min-poll-interval floor (300ms), not the tighter configured coalesce-interval (10ms)")
            .isPositive()
            .isLessThan(20);
    }
}
