package io.kestra.repository.postgres;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import org.postgresql.PGConnection;
import org.postgresql.PGNotification;

import io.kestra.core.utils.ExecutorsUtils;

import io.micronaut.configuration.jdbc.hikari.DatasourceConfiguration;
import io.micronaut.context.annotation.Context;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Maintains a single dedicated Postgres connection — outside the Hikari pool, since it must hold
 * a {@code LISTEN} registration for the process lifetime — that {@code LISTEN}s on the channels
 * {@link PgQueueSignalRegistry} has active waiters for, and forwards every {@code NOTIFY} to the
 * registry so the matching subscriber's poll loop wakes early.
 * <p>
 * This is a pure latency optimization layered on top of the existing poll + transactional ack
 * path: if this connection is down, reconnecting, or has not yet caught up on a newly-registered
 * channel, subscribers simply fall back to their regular poll backoff — no message is ever lost
 * because of it. On every (re)connect, every waiter is force-woken via {@link PgQueueSignalRegistry#signalAll()}
 * so a notification missed while disconnected still results in an immediate catch-up poll instead
 * of waiting out the full backoff.
 */
@Slf4j
@Singleton
@Context
@PostgresQueueEnabled
public class PgQueueListener implements AutoCloseable {
    // Also the upper bound on how long a newly-registered channel (a subscriber that just
    // started) waits before the listener's next LISTEN sync notices it — kept short since
    // it's a cheap local socket check, not a DB round-trip.
    private static final int NOTIFICATION_POLL_TIMEOUT_MS = 200;
    private static final long INITIAL_RECONNECT_BACKOFF_MS = 1000;
    private static final long MAX_RECONNECT_BACKOFF_MS = 30_000;

    private final DatasourceConfiguration datasourceConfiguration;
    private final PgQueueSignalRegistry registry;
    private final ExecutorService executorService;

    private volatile boolean running = true;
    private volatile Connection connection;

    @Inject
    public PgQueueListener(DatasourceConfiguration datasourceConfiguration, PgQueueSignalRegistry registry, ExecutorsUtils executorsUtils) {
        this.datasourceConfiguration = datasourceConfiguration;
        this.registry = registry;
        this.executorService = executorsUtils.singleThreadExecutor("pg-queue-listen");
    }

    @PostConstruct
    void start() {
        executorService.execute(this::run);
    }

    private void run() {
        long backoff = INITIAL_RECONNECT_BACKOFF_MS;

        while (running) {
            try {
                connect();
                // close() may have run concurrently while connect() was in flight (running flips to
                // false, but there was nothing open yet for closeConnectionQuietly() to close): bail
                // out and close the connection we just opened instead of leaking it for the rest of
                // the JVM's life.
                if (!running) {
                    closeConnectionQuietly();
                    break;
                }
                // A fresh connection has no prior LISTEN state and may have missed notifications
                // while we were disconnected: force every subscriber to catch up via a regular poll.
                registry.signalAll();
                backoff = INITIAL_RECONNECT_BACKOFF_MS;

                listenLoop();
            } catch (Exception e) {
                if (!running) {
                    break;
                }
                log.warn("Postgres queue LISTEN connection lost, reconnecting in {}ms", backoff, e);
                closeConnectionQuietly();
                sleepQuietly(backoff);
                backoff = Math.min(backoff * 2, MAX_RECONNECT_BACKOFF_MS);
            }
        }
    }

    private void listenLoop() throws SQLException {
        Set<String> listenedChannels = new HashSet<>();
        PGConnection pgConnection = (PGConnection) connection;

        while (running) {
            syncListenedChannels(listenedChannels);

            PGNotification[] notifications = pgConnection.getNotifications(NOTIFICATION_POLL_TIMEOUT_MS);
            if (notifications != null) {
                for (PGNotification notification : notifications) {
                    registry.signal(notification.getName(), notification.getParameter());
                }
            }
        }
    }

    private void connect() throws SQLException {
        connection = DriverManager.getConnection(
            datasourceConfiguration.getJdbcUrl(),
            datasourceConfiguration.getUsername(),
            datasourceConfiguration.getPassword()
        );
    }

    /**
     * Issues {@code LISTEN} for any channel the registry has gained since the last check. Channels
     * are only ever added (queue subscriptions live for the process lifetime), and {@code LISTEN}
     * is otherwise idempotent, so tracking what we already listen to just avoids redundant
     * round-trips rather than being required for correctness.
     */
    private void syncListenedChannels(Set<String> listenedChannels) throws SQLException {
        for (String channel : registry.channels()) {
            if (listenedChannels.add(channel)) {
                try (Statement statement = connection.createStatement()) {
                    // Channel names are always internally generated ("kestra_queue_" + queueName,
                    // where queueName comes from our own fixed set of message class names), never
                    // from user input; double-quoted here purely for identifier safety.
                    statement.execute("LISTEN \"" + channel + "\"");
                }
            }
        }
    }

    @PreDestroy
    @Override
    public void close() {
        running = false;
        closeConnectionQuietly();
        ExecutorsUtils.closeExecutorService("pg-queue-listen", executorService, Duration.ofSeconds(5));
    }

    private void closeConnectionQuietly() {
        Connection c = connection;
        if (c != null) {
            try {
                c.close();
            } catch (SQLException e) {
                log.debug("Failed to close Postgres queue LISTEN connection", e);
            }
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
