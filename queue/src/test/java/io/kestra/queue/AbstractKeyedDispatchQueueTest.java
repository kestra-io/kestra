package io.kestra.queue;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import io.kestra.core.queues.*;
import io.kestra.core.queues.event.KeyedDispatchEvent;
import io.kestra.core.utils.IdUtils;

import jakarta.inject.Inject;

import static io.kestra.core.utils.Rethrow.throwConsumer;
import static org.assertj.core.api.Assertions.assertThat;

public abstract class AbstractKeyedDispatchQueueTest extends AbstractQueueTest {
    private static final int DEFAULT_TIMEOUT_SECONDS = 15;

    @Inject
    private KeyedDispatchQueueInterface<TestKeyedDispatch> keyDispatchQueue;

    @Test
    void singleConsumer() throws QueueException, InterruptedException {
        String groupKey = IdUtils.create();

        CountDownLatch countDownLatch = new CountDownLatch(2);
        Collection<Integer> list = Collections.synchronizedCollection(new ArrayList<>());

        QueueSubscriber<TestKeyedDispatch> subscriber = keyDispatchQueue
            .subscriber(groupKey)
            .subscribe(e ->
            {
                list.add(e.getLeft().id);
                countDownLatch.countDown();
            });

        String prefix = this.keyPrefix();
        keyDispatchQueue.emit(groupKey, new TestKeyedDispatch(prefix + "_" + IdUtils.create(), 1));
        keyDispatchQueue.emit(groupKey, new TestKeyedDispatch(prefix + "_" + IdUtils.create(), 2));

        boolean await = countDownLatch.await(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        subscriber.close();

        assertThat(await).isEqualTo(true);
        assertThat(countDownLatch.getCount()).isEqualTo(0L);
        assertThat(list).containsExactlyInAnyOrder(1, 2);
    }

    @Test
    void multipleConsumer() throws QueueException, InterruptedException {
        String groupKey = IdUtils.create();

        int rand = ThreadLocalRandom.current().nextInt(10, 50);
        ;
        CountDownLatch countDownLatch = new CountDownLatch(rand);
        Collection<String> list = Collections.synchronizedCollection(new ArrayList<>());
        Collection<QueueSubscriber<TestKeyedDispatch>> subscribers = Collections.synchronizedCollection(new ArrayList<>());

        IntStream.range(0, 3)
            .boxed()
            .parallel()
            .forEach(
                throwConsumer(
                    i -> subscribers.add(
                        keyDispatchQueue
                            .subscriber(groupKey)
                            .subscribe(e ->
                            {
                                list.add("c" + String.format("%03d", i) + "-i" + String.format("%03d", e.getLeft().id));
                                countDownLatch.countDown();
                            })
                    )
                )
            );

        String prefix = this.keyPrefix();
        for (int i = 0; i < rand; i++) {
            keyDispatchQueue.emit(groupKey, new TestKeyedDispatch(prefix + "_" + IdUtils.create(), i));
        }

        // rebalancing can take some time, we multiply timeout
        boolean await = countDownLatch.await(DEFAULT_TIMEOUT_SECONDS * 3, TimeUnit.SECONDS);
        subscribers.parallelStream().forEach(QueueSubscriber::close);

        assertThat(await).isEqualTo(true);
        assertThat(countDownLatch.getCount()).isEqualTo(0L);
        assertThat(list).hasSize(rand);
        // based on the implementation, a consumer could process all messages
        assertThat(list.stream().map(s -> s.substring(0, s.indexOf("-"))).toList()).containsAnyOf("c000", "c001", "c002");
        assertThat(list.stream().map(s -> s.substring(s.indexOf("-") + 1)).toList()).contains("i001", String.format("i%03d", rand - 1));
    }

    @Test
    void multipleGroup() throws InterruptedException, QueueException {
        CountDownLatch countDownLatch = new CountDownLatch(6);
        List<QueueSubscriber<TestKeyedDispatch>> subscribers = new ArrayList<>();
        Map<Integer, Collection<Integer>> map = new HashMap<>();

        IntStream.range(0, 3)
            .boxed()
            .forEach(throwConsumer(i ->
            {
                map.put(i, Collections.synchronizedCollection(new ArrayList<>()));

                subscribers.add(
                    keyDispatchQueue
                        .subscriber("group-" + i)
                        .subscribe(e ->
                        {
                            map.get(i).add(e.getLeft().id);
                            countDownLatch.countDown();
                        })
                );
            }));

        String prefix = this.keyPrefix();
        for (int i = 0; i < 3; i++) {
            keyDispatchQueue.emit("group-" + i, new TestKeyedDispatch(prefix + "_" + IdUtils.create(), 1));
            keyDispatchQueue.emit("group-" + i, new TestKeyedDispatch(prefix + "_" + IdUtils.create(), 2));
        }

        boolean await = countDownLatch.await(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        subscribers.forEach(QueueSubscriber::close);

        assertThat(await).isEqualTo(true);
        assertThat(countDownLatch.getCount()).isEqualTo(0L);
        assertThat(map.entrySet().stream().flatMap(s -> s.getValue().stream()).toList()).hasSize(6);
        for (int i = 0; i < 3; i++) {
            assertThat(map.get(i)).containsExactlyInAnyOrder(1, 2);
        }
    }

    public record TestKeyedDispatch(String key, Integer id) implements KeyedDispatchEvent {
    }
}
