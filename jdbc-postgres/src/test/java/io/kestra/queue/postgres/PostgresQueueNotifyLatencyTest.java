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

import io.micronaut.context.annotation.Property;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the Postgres LISTEN/NOTIFY wake-up actually cuts delivery latency, end to end
 * (publish -&gt; pg_notify -&gt; {@link PgQueueListener} -&gt; {@link PgQueueSignalRegistry} -&gt;
 * subscriber poll loop woken early), rather than exercising only the in-JVM registry logic.
 * <p>
 * The poll backoff is deliberately set far above what a test should ever wait on a plain poll
 * (min 2s / max 5s): the test publishes only after sleeping past the point where the subscriber
 * would have already gone to sleep for its first backoff, then asserts delivery well under that
 * backoff. If the message could only be delivered here, it proves the NOTIFY path woke the
 * subscriber early rather than the fallback poll happening to catch it.
 */
@KestraTest(environments = { "test", "queue" }, rebuildContext = true)
@Property(name = "kestra.queue.type", value = "postgres") // the "queue" env sets this to "h2" (still matches @JdbcQueueEnabled generically), which would disable the @PostgresQueueEnabled beans under test
@Property(name = "kestra.jdbc.queues.min-poll-interval", value = "2s")
@Property(name = "kestra.jdbc.queues.max-poll-interval", value = "5s")
@Execution(ExecutionMode.SAME_THREAD)
class PostgresQueueNotifyLatencyTest {
    @Inject
    private DispatchQueueInterface<AbstractDispatchQueueTest.TestDispatch> dispatchQueue;

    @Inject
    private JdbcTestUtils jdbcTestUtils;

    @BeforeEach
    void init() {
        jdbcTestUtils.drop();
        jdbcTestUtils.migrate();
    }

    @Test
    void shouldDeliverWellUnderThePollBackoffOnceTheSubscriberIsAsleep() throws Exception {
        CountDownLatch received = new CountDownLatch(1);

        QueueSubscriber<AbstractDispatchQueueTest.TestDispatch> subscriber = dispatchQueue
            .subscriber()
            .subscribe(either -> received.countDown());

        try {
            // Let the subscriber's first (empty) poll happen and go to sleep for its 2s backoff, and
            // give PgQueueListener time to notice the newly-registered channel and LISTEN to it (it
            // re-syncs every ~200ms), so the message below can only be delivered via a realtime
            // wake-up, not the initial poll.
            Thread.sleep(1000);

            long start = System.nanoTime();
            dispatchQueue.emit(new AbstractDispatchQueueTest.TestDispatch(IdUtils.create(), 1));

            boolean awaited = received.await(1500, TimeUnit.MILLISECONDS);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;

            assertThat(awaited).as("message should have been delivered").isTrue();
            assertThat(elapsedMs).as("delivery latency should be far below the 2s poll backoff").isLessThan(1500);
        } finally {
            subscriber.close();
        }
    }
}
