package io.kestra.repository.postgres;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import io.kestra.queue.jdbc.client.QueueWakeRegistry;
import io.kestra.queue.poller.QueueWaker;

import jakarta.inject.Singleton;

/**
 * Registers a {@link QueueWaker} per Postgres subscriber and wakes every waiter registered on a
 * channel when {@link PgQueueListener} receives a {@code NOTIFY} for it.
 * <p>
 * For the common case (one subscriber per queue type, living for the process lifetime), the
 * registry's size is bounded by the number of active subscriptions, not by traffic — waiters are
 * never removed. VNode dispatch subscribers that get re-created on a vnode rebalance are the one
 * exception: their old waiter is abandoned rather than removed. This is a bounded, low-severity
 * limitation (a tiny Semaphore per rebalance) that does not affect correctness — see below.
 * <p>
 * Waking is a pure latency optimization: correctness never depends on it. A subscriber whose
 * notification was missed (channel not yet listened to, listener reconnecting) still gets its
 * message from the regular poll backoff.
 */
@Singleton
@PostgresQueueEnabled
public class PgQueueSignalRegistry implements QueueWakeRegistry {
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Waiter>> waitersByChannel = new ConcurrentHashMap<>();

    @Override
    public QueueWaker waker(String queueName) {
        String channel = PgQueueChannels.channelFor(queueName);
        Waiter waiter = new Waiter();

        waitersByChannel.computeIfAbsent(channel, ignored -> new CopyOnWriteArrayList<>()).add(waiter);

        return max ->
        {
            // Best-effort wait: a timeout here just means "nothing woke us, fall back to the poll".
            waiter.semaphore.tryAcquire(max.toMillis(), TimeUnit.MILLISECONDS);
        };
    }

    /**
     * Wakes every waiter registered on {@code channel}. Notifications no longer carry a
     * routing-key filter (see {@link PostgresQueueChangeNotifier}): every subscriber on the queue
     * still filters its own poll query for the rows it owns, so waking them all is correct — just
     * potentially one extra no-op poll for a subscriber whose partition wasn't the one that changed.
     */
    void signal(String channel) {
        CopyOnWriteArrayList<Waiter> waiters = waitersByChannel.get(channel);
        if (waiters == null) {
            return;
        }

        waiters.forEach(Waiter::release);
    }

    /**
     * Force-wakes every waiter across every channel. Called on every (re)connect of
     * {@link PgQueueListener} so no message published while disconnected is left waiting out its
     * full poll backoff.
     */
    void signalAll() {
        waitersByChannel.values().forEach(waiters -> waiters.forEach(Waiter::release));
    }

    /**
     * Snapshot of the channels with at least one registered waiter — the set {@link PgQueueListener}
     * must be {@code LISTEN}ing on.
     */
    Set<String> channels() {
        return Set.copyOf(waitersByChannel.keySet());
    }

    private static final class Waiter {
        private final Semaphore semaphore = new Semaphore(0);

        private void release() {
            // Coalescing: don't stack permits beyond 1, a subscriber only needs "something changed".
            if (semaphore.availablePermits() == 0) {
                semaphore.release();
            }
        }
    }
}
