package io.kestra.queue.jdbc.client;

import java.util.List;

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
     * @param routingKeys the routing keys this subscriber owns (empty = the subscriber consumes
     *        every routing key for this queue, e.g. plain dispatch or broadcast)
     * @return a waker woken early when a matching message is published, still bounded by the
     *         caller's own backoff as a fallback
     */
    QueueWaker waker(String queueName, List<String> routingKeys);
}
