package io.kestra.repository.postgres;

/**
 * Shared Postgres {@code NOTIFY} channel naming for the JDBC queue's realtime wake-up mechanism.
 * One channel per queue (not a single shared channel): Kestra's server roles subscribe to
 * disjoint queue sets, so a dedicated channel per queue lets Postgres filter {@code LISTEN}
 * delivery server-side — a process only receives notifications for queues it actually consumes.
 */
final class PgQueueChannels {
    private static final String PREFIX = "kestra_queue_";

    private PgQueueChannels() {
    }

    static String channelFor(String queueName) {
        return PREFIX + queueName;
    }
}
