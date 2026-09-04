package io.kestra.repository.postgres;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.kestra.queue.poller.QueueWaker;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the coalescing wake-up dispatch logic that decides which subscriber waker(s) a
 * Postgres NOTIFY wakes. No database involved: {@link PgQueueListener} is what talks to Postgres,
 * this only tests the in-JVM dispatch of an already-received notification.
 */
class PgQueueSignalRegistryTest {
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @AfterEach
    void tearDown() {
        scheduler.shutdownNow();
    }

    @Test
    void shouldWakeWaiterOnSignal() throws Exception {
        PgQueueSignalRegistry registry = new PgQueueSignalRegistry();
        String channel = PgQueueChannels.channelFor("worker_job_event");
        QueueWaker waker = registry.waker("worker_job_event");

        scheduler.schedule(() -> registry.signal(channel), 50, TimeUnit.MILLISECONDS);

        long elapsedMs = timeAwait(waker, Duration.ofSeconds(2));

        assertThat(elapsedMs).isLessThan(1500);
    }

    @Test
    void shouldWakeEveryWaiterOnChannel() throws Exception {
        PgQueueSignalRegistry registry = new PgQueueSignalRegistry();
        String channel = PgQueueChannels.channelFor("execution");
        QueueWaker wakerA = registry.waker("execution");
        QueueWaker wakerB = registry.waker("execution");

        scheduler.schedule(() -> registry.signal(channel), 50, TimeUnit.MILLISECONDS);

        long elapsedA = timeAwait(wakerA, Duration.ofSeconds(2));
        long elapsedB = timeAwait(wakerB, Duration.ofSeconds(2));

        assertThat(elapsedA).isLessThan(1500);
        assertThat(elapsedB).isLessThan(1500);
    }

    @Test
    void shouldNotWakeWaiterOnADifferentChannel() throws Exception {
        PgQueueSignalRegistry registry = new PgQueueSignalRegistry();
        QueueWaker waker = registry.waker("execution");

        scheduler.schedule(() -> registry.signal(PgQueueChannels.channelFor("worker_job_event")), 20, TimeUnit.MILLISECONDS);

        // The waiter is only registered on the "execution" channel: a signal for a different
        // channel must not wake it, so await() should run out its full timeout instead of returning early.
        long elapsedMs = timeAwait(waker, Duration.ofMillis(300));

        assertThat(elapsedMs).isGreaterThanOrEqualTo(250);
    }

    @Test
    void shouldCoalesceMultipleSignalsIntoASinglePermit() {
        PgQueueSignalRegistry registry = new PgQueueSignalRegistry();
        String channel = PgQueueChannels.channelFor("execution");
        QueueWaker waker = registry.waker("execution");

        // Two signals fire before anyone waits: the waiter should only ever need to catch up once.
        registry.signal(channel);
        registry.signal(channel);

        long firstAwaitMs = timeAwaitUnchecked(waker, Duration.ofMillis(300));
        long secondAwaitMs = timeAwaitUnchecked(waker, Duration.ofMillis(300));

        // First await consumes the single coalesced permit and returns immediately.
        assertThat(firstAwaitMs).isLessThan(150);
        // No permit left over from the second signal: the second await runs out its full timeout.
        assertThat(secondAwaitMs).isGreaterThanOrEqualTo(250);
    }

    @Test
    void signalAllShouldWakeEveryWaiterAcrossEveryChannel() throws Exception {
        PgQueueSignalRegistry registry = new PgQueueSignalRegistry();
        QueueWaker wakerExecution = registry.waker("execution");
        QueueWaker wakerWorkerJob = registry.waker("worker_job_event");

        scheduler.schedule(registry::signalAll, 50, TimeUnit.MILLISECONDS);

        long elapsed1 = timeAwait(wakerExecution, Duration.ofSeconds(2));
        long elapsed2 = timeAwait(wakerWorkerJob, Duration.ofSeconds(2));

        assertThat(elapsed1).isLessThan(1500);
        assertThat(elapsed2).isLessThan(1500);
    }

    @Test
    void channelsShouldReflectEveryChannelWithAtLeastOneWaiter() {
        PgQueueSignalRegistry registry = new PgQueueSignalRegistry();

        registry.waker("execution");
        registry.waker("worker_job_event");

        assertThat(registry.channels()).containsExactlyInAnyOrder("kestra_queue_execution", "kestra_queue_worker_job_event");
    }

    private static long timeAwait(QueueWaker waker, Duration max) throws InterruptedException {
        long start = System.nanoTime();
        waker.await(max);
        return Duration.ofNanos(System.nanoTime() - start).toMillis();
    }

    private static long timeAwaitUnchecked(QueueWaker waker, Duration max) {
        try {
            return timeAwait(waker, max);
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        }
    }
}
