package io.kestra.core.server;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import lombok.extern.slf4j.Slf4j;

/**
 * Coordinates a service startup with its stop so they can never overlap.
 * <p>
 * Services are started asynchronously (e.g. by the standalone runner thread pool) while
 * {@code stop()} is called from another thread (Micronaut {@code @PreDestroy} on context close).
 * Without coordination, a stop racing a still-in-flight startup tears down a partially built
 * service: it misses resources that the startup creates just after, and those resources (queue
 * subscribers, scheduled loops, gRPC servers) then outlive the application context.
 * <p>
 * Every lifecycle decision is a CAS on a single phase:
 * <ul>
 * <li>stop before start: the startup is refused ({@link #beginStart()} returns {@code false}), nothing is ever created;</li>
 * <li>stop during start: {@link #beginStop(Duration)} blocks — bounded — until the startup completes, so the teardown sees every created resource;</li>
 * <li>stop after start: no waiting.</li>
 * </ul>
 * The service must call {@link #endStart()} when its startup completes (success or failure, so a
 * failed startup cannot block a pending stop) and use its return value to decide whether it may
 * transition to RUNNING: {@code false} means a stop was requested meanwhile and the service is
 * terminating, not running.
 * The best way to use this guard is via the {@link AbstractService#guardedStart(Runnable, Runnable)} method.
 */
@Slf4j
final class ServiceLifecycleGuard {

    enum Phase {
        NEW,
        STARTING,
        STARTED,
        STOPPED
    }

    private final AtomicReference<Phase> phase = new AtomicReference<>(Phase.NEW);
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);
    private final CountDownLatch startupFinished = new CountDownLatch(1);

    /**
     * Marks the beginning of the service startup.
     *
     * @return {@code true} if the service can start, {@code false} if a stop was already requested — the service must not create any resource.
     * @throws IllegalStateException if the service was already started.
     */
    boolean beginStart() {
        if (phase.compareAndSet(Phase.NEW, Phase.STARTING)) {
            return true;
        }
        if (Phase.STOPPED == phase.get()) {
            return false;
        }
        throw new IllegalStateException("Service already started");
    }

    /**
     * Marks the end of the service startup, unblocking a stop waiting on it. Idempotent, and must
     * be called even when the startup fails.
     *
     * @return {@code true} if the service effectively started and may transition to RUNNING,
     *         {@code false} if a stop was requested in the meantime.
     */
    boolean endStart() {
        boolean started = phase.compareAndSet(Phase.STARTING, Phase.STARTED) && !stopRequested.get();
        startupFinished.countDown();
        return started;
    }

    /**
     * Marks the beginning of the service stop. When a startup is in flight, blocks until it
     * completes at most {@code startupCompletionTimeout}, after which the teardown proceeds
     * best-effort so a hung startup (e.g. an unresponsive database call) cannot block the
     * application shutdown forever.
     */
    void beginStop(Duration startupCompletionTimeout) {
        stopRequested.set(true);

        if (phase.compareAndSet(Phase.NEW, Phase.STOPPED)) {
            // the service never started: a later beginStart() will now be refused
            return;
        }

        if (Phase.STARTING == phase.get()) {
            try {
                if (!startupFinished.await(startupCompletionTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
                    log.warn("Startup did not complete within {}, proceeding with a best-effort teardown", startupCompletionTimeout);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted while waiting for the startup to complete, proceeding with a best-effort teardown");
            }
        }

        phase.set(Phase.STOPPED);
    }

    /**
     * @return {@code true} once a stop has been requested, even if it is still waiting on an in-flight startup.
     */
    boolean isStopRequested() {
        return stopRequested.get();
    }
}
