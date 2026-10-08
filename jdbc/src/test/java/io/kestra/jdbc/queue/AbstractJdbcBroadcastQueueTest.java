package io.kestra.jdbc.queue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.QueueSubscriber;
import io.kestra.jdbc.JdbcTestUtils;
import io.kestra.jdbc.QueueJdbcDataSourceProvider;
import io.kestra.queue.AbstractBroadcastQueueTest.TestBroadcast;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public abstract class AbstractJdbcBroadcastQueueTest {
    private static final int TIMEOUT_SECONDS = 30;

    @Inject
    private BroadcastQueueInterface<TestBroadcast> broadcastQueue;

    @Inject
    private QueueJdbcDataSourceProvider queueJdbcDataSourceProvider;

    @Inject
    private JdbcTestUtils jdbcTestUtils;

    @BeforeEach
    protected void init() {
        jdbcTestUtils.drop();
        jdbcTestUtils.migrate();
    }

    @Test
    void shouldDeliverAllMessagesWhenCommitOrderDiffersFromOffsetOrder() throws Exception {
        List<Integer> received = Collections.synchronizedList(new ArrayList<>());
        QueueSubscriber<TestBroadcast> subscriber = broadcastQueue.subscriber().subscribe(e -> received.add(e.getLeft().id()));

        CountDownLatch firstEmitted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);

        try (var executor = Executors.newSingleThreadExecutor()) {
            // Message 1 gets the lower offset but commits last: its transaction stays open until released.
            CompletableFuture<Void> slowPublisher = CompletableFuture.runAsync(
                () -> queueJdbcDataSourceProvider.wrapper().transaction(conf ->
                {
                    broadcastQueue.emit(new TestBroadcast("slow", 1));
                    firstEmitted.countDown();
                    releaseFirst.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                }),
                executor
            );

            try {
                await()
                    .atMost(Duration.ofSeconds(TIMEOUT_SECONDS))
                    .until(() -> firstEmitted.getCount() == 0 || slowPublisher.isCompletedExceptionally());
                assertThat(slowPublisher).isNotCompletedExceptionally();

                broadcastQueue.emit(new TestBroadcast("fast", 2));

                // Give the subscriber time to poll while message 1 is still uncommitted.
                Thread.sleep(Duration.ofSeconds(1));
            } finally {
                releaseFirst.countDown();
            }

            slowPublisher.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(received).containsExactlyInAnyOrder(1, 2));
        } finally {
            subscriber.close();
        }
    }
}
