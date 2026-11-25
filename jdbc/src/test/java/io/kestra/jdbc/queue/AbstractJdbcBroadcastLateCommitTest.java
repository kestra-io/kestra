package io.kestra.jdbc.queue;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueSubscriber;
import io.kestra.jdbc.QueueJdbcDataSourceProvider;
import io.kestra.queue.AbstractBroadcastQueueTest.TestBroadcast;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public abstract class AbstractJdbcBroadcastLateCommitTest {
    @Inject
    private BroadcastQueueInterface<TestBroadcast> testQueue;

    @Inject
    private QueueJdbcDataSourceProvider queueJdbcDataSourceProvider;

    @Test
    void shouldDeliverMessageCommittedAfterAHigherOffsetWasPolled() throws Exception {
        List<Integer> received = new CopyOnWriteArrayList<>();
        QueueSubscriber<TestBroadcast> subscriber = testQueue.subscriber().subscribe(either -> received.add(either.getLeft().id()));

        CountDownLatch firstEmitted = new CountDownLatch(1);
        CountDownLatch commitFirst = new CountDownLatch(1);
        Thread slowTransaction = new Thread(() -> queueJdbcDataSourceProvider.wrapper().transaction(configuration ->
        {
            try {
                testQueue.emit(new TestBroadcast("late", 1));
                firstEmitted.countDown();
                commitFirst.await();
            } catch (QueueException | InterruptedException e) {
                throw new IllegalStateException(e);
            }
        }));

        try {
            slowTransaction.start();
            assertThat(firstEmitted.await(10, TimeUnit.SECONDS)).isTrue();

            testQueue.emit(new TestBroadcast("early", 2));
            await().atMost(Duration.ofSeconds(10)).until(() -> received.contains(2));

            commitFirst.countDown();
            slowTransaction.join();

            await().atMost(Duration.ofSeconds(10)).until(() -> received.contains(1));
            assertThat(received).containsExactlyInAnyOrder(1, 2);
        } finally {
            commitFirst.countDown();
            subscriber.close();
        }
    }
}
