package io.kestra.queue;

import io.kestra.core.queues.QueueException;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class AbstractBroadcastQueueTest {
    private static final int DEFAULT_TIMEOUT_SECONDS = 10;

    @Inject
    private BroadcastQueueInterface<TestBroadcast> broadcastQueue;

    @Test
    void singleConsumer() throws QueueException, InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(3);
        Collection<Integer> list = Collections.synchronizedCollection(new ArrayList<>());

        io.kestra.core.utils.Disposable disposable = broadcastQueue
            .subscriber()
            .subscribe(e -> {
                list.add(e.getLeft().id);
                countDownLatch.countDown();
            });

        broadcastQueue.emit(new TestBroadcast(1));
        broadcastQueue.emit(new TestBroadcast(2));
        broadcastQueue.emit(new TestBroadcast(3));

        boolean await = countDownLatch.await(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        disposable.dispose();

        assertThat(await).isEqualTo(true);
        assertThat(countDownLatch.getCount()).isEqualTo(0L);
        assertThat(list).contains(1, 2, 3);
    }

    @Test
    void multipleConsumer() throws QueueException, InterruptedException {
        int rand = ThreadLocalRandom.current().nextInt(10, 50);;
        CountDownLatch countDownLatch = new CountDownLatch(3 * rand);
        Collection<String> list = Collections.synchronizedCollection(new ArrayList<>());
        List<io.kestra.core.utils.Disposable> disposables = new ArrayList<>();

        IntStream.range(0, rand)
            .forEach(i -> disposables.add(broadcastQueue
                .subscriber()
                .subscribe(e -> {
                    list.add("c" + String.format("%03d", i) + "-i" + String.format("%03d", e.getLeft().id));
                    countDownLatch.countDown();
                })
            ));

        broadcastQueue.emit(new TestBroadcast(1));
        broadcastQueue.emit(new TestBroadcast(2));
        broadcastQueue.emit(new TestBroadcast(3));

        boolean await = countDownLatch.await(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        io.kestra.core.utils.Disposable.of(disposables).dispose();

        assertThat(await).isEqualTo(true);
        assertThat(countDownLatch.getCount()).isEqualTo(0L);
        assertThat(list).hasSize(3 * rand);
        assertThat(list).contains("c000-i001", "c000-i002", "c000-i003");
        assertThat(list).contains("c" + String.format("%03d", (rand - 1))  +"-i001", "c" + String.format("%03d",(rand - 1))  +"-i002", "c" + String.format("%03d",(rand - 1))  +"-i003");
    }

    public record TestBroadcast(Integer id) implements BroadcastEvent {}
}
