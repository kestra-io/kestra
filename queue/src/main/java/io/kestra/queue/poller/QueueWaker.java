package io.kestra.queue.poller;

import java.time.Duration;

/**
 * Strategy used by {@link QueuePoller} to wait between two poll attempts when the previous poll
 * returned no message.
 * <p>
 * The default implementation ({@link #SLEEP}) simply sleeps for the requested duration. Backends
 * that support a push notification mechanism (e.g. Postgres {@code LISTEN}/{@code NOTIFY}) can
 * supply an implementation that returns early once a notification arrives, cutting the wait short
 * for lower latency, while still honoring {@code max} as a fallback: a poller must never block
 * longer than the configured backoff, so a missed notification still results in a poll at the
 * usual cadence — the notification is a latency optimization only, never the delivery mechanism.
 */
public interface QueueWaker {
    /**
     * A waker that simply sleeps for the given duration. Used when no realtime wake-up mechanism
     * is available for the underlying backend.
     */
    QueueWaker SLEEP = Thread::sleep;

    /**
     * Waits at most {@code max} before returning, so the caller can re-poll.
     *
     * @param max the maximum duration to wait
     * @throws InterruptedException if interrupted while waiting
     */
    void await(Duration max) throws InterruptedException;
}
