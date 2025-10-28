package io.kestra.queue;

import io.kestra.core.queues.QueueException;
import jakarta.inject.Inject;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class AbstractDispatchQueueTest {
    private static final int DEFAULT_TIMEOUT_SECONDS = 5;

    @Inject
    private DispatchQueueInterface<TestDispatch> dispatchQueue;

    @Test
    void closingConsumer() throws QueueException, InterruptedException {
        singleConsumer();
        singleConsumer();
    }


    @Test
    void singleConsumer() throws QueueException, InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(2);
        Collection<Integer> list = Collections.synchronizedCollection(new ArrayList<>());

        io.kestra.core.utils.Disposable disposable = dispatchQueue
            .subscriber()
            .subscribe(e -> {
                list.add(e.getLeft().id);
                countDownLatch.countDown();
            });

        dispatchQueue.emit(new TestDispatch(1));
        dispatchQueue.emit(new TestDispatch(2));

        boolean await = countDownLatch.await(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        disposable.dispose();

        assertThat(await).isEqualTo(true);
        assertThat(countDownLatch.getCount()).isEqualTo(0L);
        assertThat(list).containsExactlyInAnyOrder(1, 2);
    }

    @Test
    void multipleConsumer() throws QueueException, InterruptedException {
        int rand = ThreadLocalRandom.current().nextInt(10, 50);;
        CountDownLatch countDownLatch = new CountDownLatch(rand);
        Collection<String> list = Collections.synchronizedCollection(new ArrayList<>());
        List<io.kestra.core.utils.Disposable> disposables = new ArrayList<>();

        IntStream.range(0, 3)
            .forEach(i -> disposables.add(dispatchQueue
                .subscriber()
                .subscribe(e -> {
                    list.add("c" + String.format("%03d", i) + "-i" + String.format("%03d", e.getLeft().id));
                    countDownLatch.countDown();
                })
            ));

        for (int i = 0; i < rand; i++) {
            dispatchQueue.emit(new TestDispatch(i));
        }

        boolean await = countDownLatch.await(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        io.kestra.core.utils.Disposable.of(disposables).dispose();

        assertThat(await).isEqualTo(true);
        assertThat(countDownLatch.getCount()).isEqualTo(0L);
        assertThat(list).hasSize(rand);
        assertThat(list.stream().map(s -> s.substring(0, s.indexOf("-"))).toList()).contains("c000", "c001", "c002");
        assertThat(list.stream().map(s -> s.substring(s.indexOf("-") + 1)).toList()).contains("i001", String.format("i%03d", rand - 1));
    }

    @Test
    void errorProcessing() throws QueueException, InterruptedException {
        dispatchQueue.emit(List.of(new TestDispatch(1), new TestDispatch(2), new TestDispatch(3)));

        CountDownLatch countDownLatch = new CountDownLatch(4);
        Collection<Integer> list = Collections.synchronizedCollection(new ArrayList<>());

        var crashed = new AtomicBoolean(false);

        io.kestra.core.utils.Disposable disposable = dispatchQueue
            .subscriber()
            .subscribe(e -> {
                countDownLatch.countDown();

                if (e.getLeft().id == 2 && crashed.compareAndSet(false, true)) {
                    throw new Exception("Boom");
                }

                list.add(e.getLeft().id);
            });

        boolean await = countDownLatch.await(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        disposable.dispose();

        assertThat(await).isEqualTo(true);
        assertThat(countDownLatch.getCount()).isEqualTo(0L);
        assertThat(list).containsExactlyInAnyOrder(1, 2, 3);
    }

    public record TestDispatch(Integer id) implements DispatchEvent {
    }
}
