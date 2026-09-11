package io.kestra.queue.postgres;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.queues.QueueSubscriber;
import io.kestra.core.utils.IdUtils;
import io.kestra.jdbc.JdbcTestUtils;
import io.kestra.queue.AbstractDispatchQueueTest;
import io.kestra.repository.postgres.PgQueueListener;

import io.micronaut.context.annotation.Property;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the realtime wake-up is a pure latency optimization, never the delivery mechanism: with
 * {@link PgQueueListener} stopped (simulating "no active LISTEN connection" — a missed notify, or
 * the listener reconnecting), a published message is still delivered via the regular poll.
 * <p>
 * Uses its own {@code rebuildContext = true} instance (and its own test class, rather than a
 * second method sharing a class with other tests) so permanently stopping the singleton
 * {@link PgQueueListener} here cannot affect any other test.
 */
@KestraTest(environments = { "test", "queue" }, rebuildContext = true)
@Property(name = "kestra.queue.type", value = "postgres") // the "queue" env sets this to "h2" (still matches @JdbcQueueEnabled generically), which would disable the @PostgresQueueEnabled beans under test
@Execution(ExecutionMode.SAME_THREAD)
class PostgresQueueNotifyFallbackTest {
    @Inject
    private DispatchQueueInterface<AbstractDispatchQueueTest.TestDispatch> dispatchQueue;

    @Inject
    private JdbcTestUtils jdbcTestUtils;

    @Inject
    private PgQueueListener pgQueueListener;

    @BeforeEach
    void init() {
        jdbcTestUtils.drop();
        jdbcTestUtils.migrate();
    }

    @Test
    void shouldStillDeliverWhenTheListenerIsDown() throws Exception {
        // Simulate the LISTEN connection being unavailable: no NOTIFY will ever reach a subscriber.
        pgQueueListener.close();

        CountDownLatch received = new CountDownLatch(1);
        QueueSubscriber<AbstractDispatchQueueTest.TestDispatch> subscriber = dispatchQueue
            .subscriber()
            .subscribe(either -> received.countDown());

        try {
            dispatchQueue.emit(new AbstractDispatchQueueTest.TestDispatch(IdUtils.create(), 1));

            // application-test.yml keeps the default poll interval fast (10ms/100ms), so the
            // regular poll alone should deliver comfortably within a few seconds.
            assertThat(received.await(5, TimeUnit.SECONDS)).as("message should still be delivered by the fallback poll").isTrue();
        } finally {
            subscriber.close();
        }
    }
}
