package io.kestra.queue.jdbc.client;

import org.jooq.Configuration;

import jakarta.annotation.Nullable;

/**
 * Notifies a realtime wake-up mechanism (if the underlying JDBC backend supports one) that a
 * message was published, so subscribers waiting on a {@link io.kestra.queue.poller.QueueWaker}
 * can be woken before their next scheduled poll. Only Postgres provides an implementation (via
 * {@code LISTEN}/{@code NOTIFY}); the bean is absent for other dialects, in which case
 * {@link JdbcQueueClient} skips the call entirely and subscribers fall back to plain polling.
 * <p>
 * This is a pure latency optimization: the caller must remain correct if the notification is
 * silently lost (e.g. no active listener, or a listener reconnecting), since durable delivery is
 * guaranteed by the poll + transactional ack path regardless.
 */
public interface QueueChangeNotifier {
    /**
     * Signals that a message was published to {@code queueName} (and, for keyed/VNode dispatch
     * queues, partitioned by {@code routingKey}), using the same transaction as the INSERT so the
     * signal is only observable once the message is durably committed.
     *
     * @param configuration the jOOQ transaction configuration the INSERT ran in
     * @param queueName the published queue name
     * @param routingKey the message's routing key, or {@code null}/empty for plain dispatch and broadcast
     */
    void notifyChange(Configuration configuration, String queueName, @Nullable String routingKey);
}
