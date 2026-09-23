package io.kestra.repository.postgres;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import io.kestra.core.utils.ExecutorsUtils;
import io.kestra.core.utils.ThreadMainFactoryBuilder;
import io.kestra.jdbc.runner.JdbcQueueConfiguration;
import io.kestra.queue.jdbc.client.QueueChangeNotifier;

import io.micronaut.configuration.jdbc.hikari.DatasourceConfiguration;
import io.micronaut.context.annotation.Context;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Emits a Postgres {@code NOTIFY} on the channel for a published queue, after the publish
 * transaction has committed, using a single dedicated connection outside the Hikari pool (mirroring
 * {@link PgQueueListener}) — never the transaction's own connection. Emitting on the commit path
 * would serialize every publish through Postgres's global NOTIFY-delivery lock, which is the
 * mechanism that collapsed throughput under load in earlier benchmarking; keeping NOTIFY off that
 * path removes the bottleneck entirely.
 * <p>
 * Emission is also coalesced per channel: a burst of publishes to the same queue collapses into at
 * most one {@code NOTIFY} per {@link PostgresQueueNotifyConfiguration#coalesceInterval()} (leading
 * edge immediate, then rate-limited), so a busy queue can't wake its subscriber on every single
 * message — the fix for the second half of the same regression, where the wake-up storm amplified
 * poll-query traffic against the {@code queues} table.
 * <p>
 * The effective coalescing interval is additionally floored at the subscriber's own
 * {@link JdbcQueueConfiguration#minPollInterval()}: a subscriber's poll loop already re-polls at
 * that cadence on its own once it has just been active (see {@code QueuePoller}'s empty-poll
 * backoff), so a NOTIFY tighter than that floor cannot buy any extra latency in the "briefly idle
 * under sustained load" case — it can only push a hot subscriber to poll (and pay a full
 * transaction) more often than plain polling ever would. The floor never affects the "genuinely
 * idle, then a message arrives" case, where the leading-edge immediate emit still fires at once,
 * cutting the escalated backoff (up to {@code maxPollInterval}) down to the floor.
 * <p>
 * This is still a pure latency optimization: a coalesced-away or otherwise lost signal never loses
 * a message, since durable delivery is guaranteed by the poll + transactional ack path regardless.
 */
@Slf4j
@Singleton
@Context
@PostgresQueueEnabled
public class PostgresQueueChangeNotifier implements QueueChangeNotifier, AutoCloseable {
    private static final long INITIAL_RECONNECT_BACKOFF_MS = 1000;
    private static final long MAX_RECONNECT_BACKOFF_MS = 30_000;

    private final DatasourceConfiguration datasourceConfiguration;
    private final PostgresQueueNotifyConfiguration configuration;
    private final ScheduledExecutorService executorService;

    /**
     * {@code max(coalesceInterval, minPollInterval)}, computed once since both inputs are static
     * configuration — see the class javadoc for why NOTIFY must never undercut the poll floor.
     */
    private final long minEmitIntervalMs;

    /**
     * Channels with an emit already scheduled (immediate or delayed) but not yet sent: guards
     * against scheduling more than one pending emit per channel within a coalescing window.
     */
    private final Set<String> pendingChannels = ConcurrentHashMap.newKeySet();

    /**
     * Last time (in {@link System#nanoTime()} units) a NOTIFY was actually sent for a channel, used
     * to compute how long the next emit for that channel must still wait to respect
     * {@link PostgresQueueNotifyConfiguration#coalesceInterval()}.
     */
    private final ConcurrentHashMap<String, Long> lastEmitNanos = new ConcurrentHashMap<>();

    private volatile boolean running = true;
    private volatile Connection connection;
    private volatile long reconnectBackoffMs = INITIAL_RECONNECT_BACKOFF_MS;

    @Inject
    public PostgresQueueChangeNotifier(DatasourceConfiguration datasourceConfiguration, PostgresQueueNotifyConfiguration configuration, JdbcQueueConfiguration jdbcQueueConfiguration) {
        this.datasourceConfiguration = datasourceConfiguration;
        this.configuration = configuration;
        this.minEmitIntervalMs = Math.max(configuration.coalesceInterval().toMillis(), jdbcQueueConfiguration.minPollInterval().toMillis());
        // Single thread: every NOTIFY (immediate or coalesced) and every (re)connect attempt runs
        // here, so the dedicated connection is never touched from more than one thread at a time.
        this.executorService = Executors.newSingleThreadScheduledExecutor(ThreadMainFactoryBuilder.build("pg-queue-notify_%d"));
    }

    @PostConstruct
    void start() {
        if (configuration.enabled()) {
            executorService.execute(this::connect);
        }
    }

    @Override
    public void notifyChange(String queueName) {
        if (!configuration.enabled()) {
            return;
        }

        String channel = PgQueueChannels.channelFor(queueName);
        if (!pendingChannels.add(channel)) {
            // An emit for this channel is already scheduled within the current coalescing window.
            return;
        }

        Long lastEmit = lastEmitNanos.get(channel);
        long sinceLastEmitMs = lastEmit == null ? Long.MAX_VALUE : Duration.ofNanos(System.nanoTime() - lastEmit).toMillis();
        long delayMs = Math.max(0, minEmitIntervalMs - sinceLastEmitMs);

        try {
            executorService.schedule(() -> emit(channel), delayMs, TimeUnit.MILLISECONDS);
        } catch (RuntimeException e) {
            // The executor may already be shutting down (app stop racing a publish). This is a pure
            // latency optimization: never let a scheduling failure surface as a publish failure to
            // the caller, and don't leave the channel stuck "pending" since emit() will never run to
            // clear it.
            pendingChannels.remove(channel);
            log.debug("Unable to schedule a Postgres NOTIFY for [{}], subscribers fall back to the poll", channel, e);
        }
    }

    private void emit(String channel) {
        pendingChannels.remove(channel);
        lastEmitNanos.put(channel, System.nanoTime());

        Connection c = connection;
        if (c == null) {
            log.debug("Skipping Postgres NOTIFY on [{}]: no active LISTEN/NOTIFY connection, subscribers fall back to the poll", channel);
            return;
        }

        try (PreparedStatement statement = c.prepareStatement("SELECT pg_notify(?, '')")) {
            statement.setString(1, channel);
            statement.execute();
        } catch (SQLException e) {
            log.warn("Failed to emit Postgres NOTIFY on [{}], reconnecting", channel, e);
            closeConnectionQuietly();
            connection = null;
            executorService.execute(this::connect);
        }
    }

    private void connect() {
        if (!running) {
            return;
        }

        try {
            connection = DriverManager.getConnection(
                datasourceConfiguration.getJdbcUrl(),
                datasourceConfiguration.getUsername(),
                datasourceConfiguration.getPassword()
            );

            if (!running) {
                // close() ran concurrently while this connection was being established: don't leak it.
                closeConnectionQuietly();
                connection = null;
                return;
            }

            reconnectBackoffMs = INITIAL_RECONNECT_BACKOFF_MS;
        } catch (SQLException e) {
            log.warn("Postgres queue NOTIFY connection failed, retrying in {}ms", reconnectBackoffMs, e);
            if (running) {
                executorService.schedule(this::connect, reconnectBackoffMs, TimeUnit.MILLISECONDS);
                reconnectBackoffMs = Math.min(reconnectBackoffMs * 2, MAX_RECONNECT_BACKOFF_MS);
            }
        }
    }

    @PreDestroy
    @Override
    public void close() {
        running = false;
        closeConnectionQuietly();
        ExecutorsUtils.closeExecutorService("pg-queue-notify", executorService, Duration.ofSeconds(5));
    }

    private void closeConnectionQuietly() {
        Connection c = connection;
        if (c != null) {
            try {
                c.close();
            } catch (SQLException e) {
                log.debug("Failed to close Postgres queue NOTIFY connection", e);
            }
        }
    }
}
