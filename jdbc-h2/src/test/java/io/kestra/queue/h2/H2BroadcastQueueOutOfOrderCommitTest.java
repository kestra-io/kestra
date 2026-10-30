package io.kestra.queue.h2;

import java.util.*;
import java.util.concurrent.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.QueueSubscriber;
import io.kestra.jdbc.JdbcTestUtils;
import io.kestra.jdbc.QueueJdbcDataSourceProvider;
import io.kestra.queue.AbstractBroadcastQueueTest.TestBroadcast;
import io.kestra.queue.jdbc.client.JdbcQueueClient;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest(environments = { "test", "queue" })
class H2BroadcastQueueOutOfOrderCommitTest {
    @Inject
    JdbcTestUtils jdbcTestUtils;

    @Inject
    QueueJdbcDataSourceProvider queueJdbcDataSourceProvider;

    @Inject
    JdbcQueueClient jdbcQueueClient;

    @Inject
    BroadcastQueueInterface<TestBroadcast> broadcastQueue;

    @BeforeEach
    protected void init() {
        jdbcTestUtils.drop();
        jdbcTestUtils.migrate();
    }

    @Test
    void shouldDeliverOutOrOrderCommittedMessagesWithoutLoss() throws Exception {
        CountDownLatch txAInserted = new CountDownLatch(1);
        CountDownLatch allowTxACommit = new CountDownLatch(1);
        CountDownLatch txBCommitted = new CountDownLatch(1);

        List<Integer> received = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch receivedBoth = new CountDownLatch(2);

        QueueSubscriber<TestBroadcast> subscriber = broadcastQueue.subscriber().subscribe(either ->
        {
            if (either.isLeft()) {
                received.add(either.getLeft().id());
                receivedBoth.countDown();
            }
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            // Tx A: inserts message-A (allocated lower offset), but pauses before commit
            executor.submit(() ->
            {
                queueJdbcDataSourceProvider.wrapper().transaction(conf ->
                {
                    try {
                        broadcastQueue.emit(new TestBroadcast("key-A", 1));
                        txAInserted.countDown();
                        allowTxACommit.await(10, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
            });

            // Wait for Tx A to insert
            assertThat(txAInserted.await(10, TimeUnit.SECONDS)).isTrue();

            // Tx B: inserts message-B (allocated higher offset) and commits immediately
            executor.submit(() ->
            {
                queueJdbcDataSourceProvider.wrapper().transaction(conf ->
                {
                    try {
                        broadcastQueue.emit(new TestBroadcast("key-B", 2));
                        txBCommitted.countDown();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
            });

            assertThat(txBCommitted.await(10, TimeUnit.SECONDS)).isTrue();

            // At this point, Tx B committed offset N+1, but Tx A (offset N) has not committed yet.
            // Give subscriber time to poll and see message-B while Tx A is still uncommitted.
            Thread.sleep(150);

            // Now allow Tx A to commit offset N
            allowTxACommit.countDown();

            // Both messages must be delivered!
            boolean completed = receivedBoth.await(10, TimeUnit.SECONDS);
            subscriber.close();

            assertThat(completed).isTrue();
            assertThat(received).containsExactlyInAnyOrder(1, 2);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldDeliverUnderInterleavedQueues() throws Exception {
        List<Integer> received = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch receivedBoth = new CountDownLatch(2);

        QueueSubscriber<TestBroadcast> subscriber = broadcastQueue.subscriber().subscribe(either ->
        {
            if (either.isLeft()) {
                received.add(either.getLeft().id());
                receivedBoth.countDown();
            }
        });

        // Broadcast 1
        broadcastQueue.emit(new TestBroadcast("key-1", 100));

        // Interleave rows from another queue to advance sequence with gaps
        for (int i = 0; i < 5; i++) {
            jdbcQueueClient.publish("other-queue", null, "other-" + i, "{}");
        }

        // Broadcast 2
        broadcastQueue.emit(new TestBroadcast("key-2", 200));

        boolean completed = receivedBoth.await(10, TimeUnit.SECONDS);
        subscriber.close();

        assertThat(completed).isTrue();
        assertThat(received).containsExactly(100, 200);
    }

    @Test
    void shouldSurviveRolledBackTransaction() throws Exception {
        CountDownLatch txRollbackDone = new CountDownLatch(1);
        List<Integer> received = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch receivedMsg = new CountDownLatch(1);

        QueueSubscriber<TestBroadcast> subscriber = broadcastQueue.subscriber().subscribe(either ->
        {
            if (either.isLeft()) {
                received.add(either.getLeft().id());
                receivedMsg.countDown();
            }
        });

        // Insert and rollback to burn an offset
        try {
            queueJdbcDataSourceProvider.wrapper().transaction(conf ->
            {
                try {
                    broadcastQueue.emit(new TestBroadcast("key-dead", 999));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                throw new IllegalStateException("Simulated rollback");
            });
        } catch (IllegalStateException expected) {
            txRollbackDone.countDown();
        }

        assertThat(txRollbackDone.await(5, TimeUnit.SECONDS)).isTrue();

        // Emit a legitimate message after the rolled-back offset
        broadcastQueue.emit(new TestBroadcast("key-alive", 1000));

        boolean completed = receivedMsg.await(10, TimeUnit.SECONDS);
        subscriber.close();

        assertThat(completed).isTrue();
        assertThat(received).containsExactly(1000);
    }

    @Test
    void shouldDeliverLargeBurstUnderConcurrentPublishers() throws Exception {
        int messageCount = 100;
        List<Integer> received = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch allReceived = new CountDownLatch(messageCount);

        QueueSubscriber<TestBroadcast> subscriber = broadcastQueue.subscriber().subscribe(either ->
        {
            if (either.isLeft()) {
                received.add(either.getLeft().id());
                allReceived.countDown();
            }
        });

        ExecutorService publishExecutor = Executors.newFixedThreadPool(4);
        try {
            for (int i = 0; i < messageCount; i++) {
                final int idx = i;
                publishExecutor.submit(() ->
                {
                    try {
                        // Some publish in short transaction with random jitter to create out-of-order commits
                        if (idx % 2 == 0) {
                            queueJdbcDataSourceProvider.wrapper().transaction(conf ->
                            {
                                try {
                                    Thread.sleep(ThreadLocalRandom.current().nextInt(1, 10));
                                    broadcastQueue.emit(new TestBroadcast("key-" + idx, idx));
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            });
                        } else {
                            broadcastQueue.emit(new TestBroadcast("key-" + idx, idx));
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
            }

            boolean completed = allReceived.await(20, TimeUnit.SECONDS);
            subscriber.close();

            assertThat(completed).isTrue();
            assertThat(received).hasSize(messageCount);
        } finally {
            publishExecutor.shutdownNow();
        }
    }
}
