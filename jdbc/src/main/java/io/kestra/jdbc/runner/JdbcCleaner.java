package io.kestra.jdbc.runner;

import java.util.Objects;

import io.micronaut.context.annotation.Requires;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Scheduled trigger that periodically purges the <code>queues</code> table on the executor.
 * <p>
 * The purge logic lives in {@link JdbcQueueCleaner} so it can be shared with the
 * <code>sys purge-queue</code> CLI command; this class only owns the schedule and the server-type gate.
 */
@Singleton
@JdbcRunnerEnabled
@Slf4j
@Requires(property = "kestra.jdbc.cleaner")
@Requires(property = "kestra.server-type", pattern = "(EXECUTOR|STANDALONE)")
public class JdbcCleaner {
    private final JdbcQueueCleaner queueCleaner;

    @Inject
    public JdbcCleaner(JdbcQueueCleaner queueCleaner) {
        this.queueCleaner = Objects.requireNonNull(queueCleaner, "queueCleaner must not be null");
    }

    @Scheduled(initialDelay = "${kestra.jdbc.cleaner.initial-delay}", fixedDelay = "${kestra.jdbc.cleaner.fixed-delay}")
    public long deleteQueue() {
        return queueCleaner.purge();
    }
}
