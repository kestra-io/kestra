package io.kestra.repository.postgres;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import io.kestra.queue.jdbc.client.QueueWakeRegistry;
import io.kestra.queue.poller.QueueWaker;

import jakarta.inject.Singleton;

/**
 * Registers a {@link QueueWaker} per Postgres subscriber and wakes the matching ones when
 * {@link PgQueueListener} receives a {@code NOTIFY}.
 * <p>
 * For the common case (one subscriber per queue type, living for the process lifetime), the
 * registry's size is bounded by the number of active subscriptions, not by traffic — waiters are
 * never removed. VNode dispatch subscribers that get re-created on a vnode rebalance are the one
 * exception: their old waiter is abandoned rather than removed. This is a bounded, low-severity
 * limitation (a tiny Semaphore + Set per rebalance) that does not affect correctness — see below.
 * <p>
 * Waking is a pure latency optimization: correctness never depends on it. A subscriber whose
 * notification was missed (channel not yet listened to, listener reconnecting, routing-key filter
 * momentarily stale after a VNode rebalance) still gets its message from the regular poll backoff.
 */
@Singleton
@PostgresQueueEnabled
public class PgQueueSignalRegistry implements QueueWakeRegistry {
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Waiter>> waitersByChannel = new ConcurrentHashMap<>();

    @Override
    public QueueWaker waker(String queueName, List<String> routingKeys) {
        String channel = PgQueueChannels.channelFor(queueName);
        Waiter waiter = new Waiter(Set.copyOf(routingKeys));

        waitersByChannel.computeIfAbsent(channel, ignored -> new CopyOnWriteArrayList<>()).add(waiter);

        return max ->
        {
            // Best-effort wait: a timeout here just means "nothing woke us, fall back to the poll".
            waiter.semaphore.tryAcquire(max.toMillis(), TimeUnit.MILLISECONDS);
        };
    }

    /**
     * Wakes every waiter registered on {@code channel} whose owned routing keys match
     * {@code routingKey} (or every waiter, if {@code routingKey} is empty — plain dispatch and
     * broadcast publishes carry no routing key).
     */
    void signal(String channel, String routingKey) {
        CopyOnWriteArrayList<Waiter> waiters = waitersByChannel.get(channel);
        if (waiters == null) {
            return;
        }

        boolean wakeAll = routingKey == null || routingKey.isEmpty();
        for (Waiter waiter : waiters) {
            if (wakeAll || waiter.routingKeys.isEmpty() || waiter.routingKeys.contains(routingKey)) {
                waiter.release();
            }
        }
    }

    /**
     * Force-wakes every waiter across every channel, bypassing the routing-key filter. Called on
     * every (re)connect of {@link PgQueueListener} so no message published while disconnected is
     * left waiting out its full poll backoff.
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
        private final Set<String> routingKeys;
        private final Semaphore semaphore = new Semaphore(0);

        private Waiter(Set<String> routingKeys) {
            this.routingKeys = routingKeys;
        }

        private void release() {
            // Coalescing: don't stack permits beyond 1, a subscriber only needs "something changed".
            if (semaphore.availablePermits() == 0) {
                semaphore.release();
            }
        }
    }
}
