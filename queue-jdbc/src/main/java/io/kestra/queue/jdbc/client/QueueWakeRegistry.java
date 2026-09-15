package io.kestra.queue.jdbc.client;

import io.kestra.queue.poller.QueueWaker;

/**
 * Supplies a {@link QueueWaker} that a JDBC subscriber's poll loop waits on between two polls,
 * backed by a realtime wake-up mechanism (Postgres {@code LISTEN}/{@code NOTIFY}). Only present
 * for dialects that support one; absent otherwise, in which case subscribers fall back to the
 * default sleep-based {@link QueueWaker#SLEEP}.
 */
public interface QueueWakeRegistry {
    /**
     * @param queueName the queue this subscriber polls
     * @return a waker woken early when a message is published to {@code queueName}, still bounded
     *         by the caller's own backoff as a fallback
     */
    QueueWaker waker(String queueName);
}
