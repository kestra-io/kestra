package io.kestra.queue;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;

import io.kestra.core.queues.*;
import io.kestra.core.queues.event.BroadcastEvent;
import io.kestra.core.utils.IdUtils;

import jakarta.inject.Inject;

import static io.kestra.core.utils.Rethrow.throwConsumer;
import static org.assertj.core.api.Assertions.assertThat;

public abstract class AbstractBroadcastQueueTest extends AbstractQueueTest {
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final long RESUME_SETTLE_MS = 300;

    @Inject
    private BroadcastQueueInterface<TestBroadcast> broadcastQueue;

    @Test
    void singleConsumer() throws QueueException, InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(3);
        Collection<Integer> list = Collections.synchronizedCollection(new ArrayList<>());

        QueueSubscriber<TestBroadcast> subscriber = broadcastQueue
            .subscriber()
            .subscribe(e ->
            {
                list.add(e.getLeft().id);
                countDownLatch.countDown();
            });

        String prefix = this.keyPrefix();
        for (int i = 1; i <= 3; i++) {
            broadcastQueue.emit(new TestBroadcast(prefix + "_" + IdUtils.create(), i));
        }

        boolean await = countDownLatch.await(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        subscriber.close();

        assertThat(await).isEqualTo(true);
        assertThat(countDownLatch.getCount()).isEqualTo(0L);
        assertThat(list).contains(1, 2, 3);
    }

    @Test
    void closingConsumer() throws QueueException, InterruptedException {
        singleConsumer();
        singleConsumer();
    }

    @Test
    void multipleConsumer() throws QueueException, InterruptedException {
        int rand = ThreadLocalRandom.current().nextInt(10, 50);
        ;
        CountDownLatch countDownLatch = new CountDownLatch(3 * rand);
        Collection<String> list = Collections.synchronizedCollection(new ArrayList<>());
        Collection<QueueSubscriber<TestBroadcast>> subscribers = Collections.synchronizedCollection(new ArrayList<>());

        IntStream.range(0, rand)
            .boxed()
            .parallel()
            .forEach(
                throwConsumer(
                    i -> subscribers.add(
                        broadcastQueue
                            .subscriber()
                            .subscribe(e ->
                            {
                                list.add("c" + String.format("%03d", i) + "-i" + String.format("%03d", e.getLeft().id));
                                countDownLatch.countDown();
                            })
                    )
                )
            );

        String prefix = this.keyPrefix();
        broadcastQueue.emit(new TestBroadcast(prefix + "_" + IdUtils.create(), 1));
        broadcastQueue.emit(new TestBroadcast(prefix + "_" + IdUtils.create(), 2));
        broadcastQueue.emit(new TestBroadcast(prefix + "_" + IdUtils.create(), 3));

        // rebalancing can take some time, we multiply timeout
        boolean await = countDownLatch.await(DEFAULT_TIMEOUT_SECONDS * 3, TimeUnit.SECONDS);
        subscribers.parallelStream().forEach(QueueSubscriber::close);

        assertThat(await).isEqualTo(true);
        assertThat(countDownLatch.getCount()).isEqualTo(0L);
        assertThat(list).hasSize(3 * rand);
        assertThat(list).contains("c000-i001", "c000-i002", "c000-i003");
        // all message sent to all consumers
        IntStream.range(1, 4).boxed().forEach(i ->
        {
            assertThat(list.stream().filter(s -> s.endsWith(String.format("-i%03d", i))).count()).isEqualTo(rand);
        });
        // all consumers received all messages
        IntStream.range(0, rand).boxed().forEach(i ->
        {
            assertThat(list.stream().filter(s -> s.startsWith(String.format("c%03d", i))).count()).isEqualTo(3L);
        });
        assertThat(list).contains("c" + String.format("%03d", (rand - 1)) + "-i001", "c" + String.format("%03d", (rand - 1)) + "-i002", "c" + String.format("%03d", (rand - 1)) + "-i003");
    }

    @Test
    void pause() throws QueueException, InterruptedException {
        CountDownLatch countDownLatchFirst = new CountDownLatch(1);
        CountDownLatch countDownLatchSecond = new CountDownLatch(2);
        CountDownLatch countDownLatchOthers = new CountDownLatch(2);
        Collection<Pair<Instant, Integer>> list = Collections.synchronizedCollection(new ArrayList<>());

        QueueSubscriber<TestBroadcast> subscriber = broadcastQueue
            .subscriber()
            .subscribe(e ->
            {
                list.add(Pair.of(Instant.now(), e.getLeft().id));
                if (e.getLeft().id == 1) {
                    countDownLatchFirst.countDown();
                } else if (e.getLeft().id <= 3) {
                    countDownLatchSecond.countDown();
                } else {
                    countDownLatchOthers.countDown();
                }
            });

        String prefix = this.keyPrefix();
        // first round
        broadcastQueue.emit(new TestBroadcast(prefix + "_" + IdUtils.create(), 1));

        boolean await1 = countDownLatchFirst.await(DEFAULT_TIMEOUT_SECONDS + 10, TimeUnit.SECONDS);
        subscriber.pause();
        assertThat(await1).isTrue();

        // second round
        Instant resumeTime = Instant.now();
        subscriber.resume();
        // On brokers without message persistence (e.g. Redis pub/sub), resume triggers an
        // async resubscribe roundtrip; emitting immediately can publish before the subscription
        // is re-established, causing messages to be dropped.
        Thread.sleep(RESUME_SETTLE_MS);

        broadcastQueue.emit(new TestBroadcast(prefix + "_" + IdUtils.create(), 2));
        broadcastQueue.emit(new TestBroadcast(prefix + "_" + IdUtils.create(), 3));

        boolean await2 = countDownLatchSecond.await(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        subscriber.pause();
        assertThat(await2).isTrue();

        // last round
        Instant resumeTime2 = Instant.now();
        subscriber.resume();
        Thread.sleep(RESUME_SETTLE_MS);

        broadcastQueue.emit(new TestBroadcast(prefix + "_" + IdUtils.create(), 4));
        broadcastQueue.emit(new TestBroadcast(prefix + "_" + IdUtils.create(), 5));

        boolean await3 = countDownLatchOthers.await(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        subscriber.close();

        assertThat(await3).isEqualTo(true);
        assertThat(list).hasSize(5);
        assertThat(list.stream().filter(i -> i.getLeft().isBefore(resumeTime)).count()).isEqualTo(1);
        assertThat(list.stream().filter(i -> i.getLeft().isAfter(resumeTime)).count()).isEqualTo(4);
        assertThat(list.stream().filter(i -> i.getLeft().isAfter(resumeTime2)).count()).isEqualTo(2);
    }

    public record TestBroadcast(String key, Integer id) implements BroadcastEvent {
    }
}
