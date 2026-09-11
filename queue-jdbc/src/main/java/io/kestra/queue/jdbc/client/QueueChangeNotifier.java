package io.kestra.queue.jdbc.client;

/**
 * Notifies a realtime wake-up mechanism (if the underlying JDBC backend supports one) that a
 * message was published, so subscribers waiting on a {@link io.kestra.queue.poller.QueueWaker}
 * can be woken before their next scheduled poll. Only Postgres provides an implementation (via
 * {@code LISTEN}/{@code NOTIFY}); the bean is absent for other dialects, in which case
 * {@link JdbcQueueClient} skips the call entirely and subscribers fall back to plain polling.
 * <p>
 * This is a pure latency optimization: the caller must remain correct if the notification is
 * silently lost or coalesced away (e.g. no active listener, a listener reconnecting, or a
 * rate-limiting implementation dropping a redundant signal), since durable delivery is guaranteed
 * by the poll + transactional ack path regardless.
 */
public interface QueueChangeNotifier {
    /**
     * Signals that at least one message was published to {@code queueName}. Called after the
     * publish transaction has committed, so a woken subscriber is guaranteed to see the row (and,
     * unlike an in-transaction signal, the notification never contends with the transaction's own
     * commit).
     *
     * @param queueName the published queue name
     */
    void notifyChange(String queueName);
}
