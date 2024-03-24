package io.kestra.core.server;

import com.google.common.annotations.VisibleForTesting;
import io.micronaut.core.annotation.Introspected;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Base class for scheduling a task that operate on Worker liveness.
 */
@Introspected
@Slf4j
public abstract class AbstractServiceLivenessTask implements Runnable, AutoCloseable {

    private final String name;
    protected final ServerConfig serverConfig;
    private final AtomicBoolean isStopped = new AtomicBoolean(false);
    private ScheduledExecutorService scheduledExecutorService;
    private Instant lastScheduledExecution;

    /**
     * Creates a new {@link AbstractServiceLivenessTask} instance.
     *
     * @param name          the task name.
     * @param configuration the liveness configuration.
     */
    protected AbstractServiceLivenessTask(final String name,
                                          final ServerConfig configuration) {
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.serverConfig = Objects.requireNonNull(configuration, "serverConfig cannot be null");
        this.lastScheduledExecution = Instant.now();
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public void run() {
        final Instant now = Instant.now();
        run(now);
    }

    @VisibleForTesting
    public void run(final Instant now) {
        try {
            long elapsed = getElapsedMilliSinceLastSchedule(now);
            long timeout = serverConfig.liveness().timeout().toMillis();
            if (elapsed > timeout) {
                // useful for debugging unexpected heartbeat timeout
                log.warn("Thread starvation or clock leap detected (elapsed since previous schedule {}ms", elapsed);
            }
            onSchedule(now);
        } catch (Exception e) {
            log.error("Unexpected error while executing '{}'. Error: {}", name, e.getMessage(), e);
        } finally {
            lastScheduledExecution = now;
        }
    }

    protected Instant lastScheduledExecution() {
        return lastScheduledExecution;
    }

    protected long getElapsedMilliSinceLastSchedule(final Instant now) {
        return now.toEpochMilli() - lastScheduledExecution.toEpochMilli();
    }

    /**
     * The callback method invoked on each schedule.
     *
     * @param now the time of the execution.
     * @throws Exception when something goes wrong during the execution.
     */
    protected abstract void onSchedule(final Instant now) throws Exception;

    /**
     * Starts this task.
     */
    @PostConstruct
    public void start() {
        if (!isLivenessEnabled()) {
            log.warn(
                "Server liveness is currently disabled (kestra.server.liveness.enabled=false) " +
                "If you are running in production environment, please ensure this property is configured to 'true'. "
            );
        }
        if (scheduledExecutorService == null && !isStopped.get()) {
            scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, name));
            Duration scheduleInterval = getScheduleInterval();
            log.debug("Scheduling '{}' at fixed rate {}.", name, scheduleInterval);
            scheduledExecutorService.scheduleAtFixedRate(
                this,
                0,
                scheduleInterval.toSeconds(),
                TimeUnit.SECONDS
            );
        } else {
            throw new IllegalStateException(
                "The task '" + name + "' is either already started or already stopped, cannot re-start");
        }
    }

    /**
     * Checks whether liveness is enabled.
     *
     * @return {@code true} if liveness is enabled.
     */
    protected Boolean isLivenessEnabled() {
        return serverConfig.liveness().enabled();
    }

    /**
     * Returns the fixed rate duration for scheduling this task.
     *
     * @return a {@link Duration}.
     */
    protected abstract Duration getScheduleInterval();

    /**
     * Closes this task.
     */
    @PreDestroy
    @Override
    public void close() {
        if (isStopped.compareAndSet(false, true) && scheduledExecutorService != null) {
            scheduledExecutorService.shutdown();
            log.debug("Stopped scheduled '{}' task.", name);
        }
    }
}
