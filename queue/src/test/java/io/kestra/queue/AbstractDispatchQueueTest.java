package io.kestra.queue;

import io.kestra.core.contexts.KestraContext;
import io.kestra.core.queues.*;
import io.kestra.core.queues.event.DispatchEvent;
import io.kestra.core.services.IgnoreExecutionService;
import io.kestra.core.utils.IdUtils;
import jakarta.inject.Inject;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

import io.kestra.core.queues.*;

import static io.kestra.core.utils.Rethrow.throwConsumer;
import static org.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;
import static org.junit.jupiter.api.Assertions.assertThrows;

public abstract class AbstractDispatchQueueTest extends AbstractQueueTest {
    private static final int DEFAULT_TIMEOUT_SECONDS = 15;

    @Inject
    private DispatchQueueInterface<TestDispatch> dispatchQueue;

    @Inject
    private IgnoreExecutionService ignoreExecutionService;

    private KestraContext realContext;
    protected NoOpShutdownContext noOpShutdownContext;

    @BeforeEach
    void swapKestraContext() {
        realContext = KestraContext.getContext();
        noOpShutdownContext = new NoOpShutdownContext(realContext, new AtomicBoolean(false));
        KestraContext.setContext(noOpShutdownContext);
    }

    @AfterEach
    void restoreKestraContext() {
        KestraContext.setContext(realContext);
    }

    @Test
    void singleConsumer() throws QueueException, InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(2);
        Collection<Integer> list = Collections.synchronizedCollection(new ArrayList<>());

        QueueSubscriber<TestDispatch> subscriber = dispatchQueue
            .subscriber()
            .subscribe(e ->
            {
                list.add(e.getLeft().id);
                countDownLatch.countDown();
            });

        String prefix = this.keyPrefix();
        dispatchQueue.emit(new TestDispatch(prefix + "_" + IdUtils.create(), 1));
        dispatchQueue.emit(new TestDispatch(prefix + "_" + IdUtils.create(), 2));

        boolean await = countDownLatch.await(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        subscriber.close();

        assertThat(await).isEqualTo(true);
        assertThat(countDownLatch.getCount()).isEqualTo(0L);
        assertThat(list).containsExactlyInAnyOrder(1, 2);
    }

    @Test
    void batchConsumer() throws QueueException, InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(2);
        Collection<Integer> list = Collections.synchronizedCollection(new ArrayList<>());

        QueueSubscriber<TestDispatch> subscriber = dispatchQueue
            .subscriber()
            .subscribeBatch(items ->
            {
                items.forEach(e ->
                {
                    list.add(e.getLeft().id);
                    countDownLatch.countDown();
                });
            });

        String prefix = this.keyPrefix();
        dispatchQueue.emit(new TestDispatch(prefix + "_" + IdUtils.create(), 1));
        dispatchQueue.emit(new TestDispatch(prefix + "_" + IdUtils.create(), 2));

        boolean await = countDownLatch.await(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        subscriber.close();

        assertThat(await).isEqualTo(true);
        assertThat(countDownLatch.getCount()).isEqualTo(0L);
        assertThat(list).containsExactlyInAnyOrder(1, 2);
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
        CountDownLatch countDownLatch = new CountDownLatch(rand);
        Collection<String> list = Collections.synchronizedCollection(new ArrayList<>());
        Collection<QueueSubscriber<TestDispatch>> subscribers = Collections.synchronizedCollection(new ArrayList<>());

        IntStream.range(0, 3)
            .boxed()
            .parallel()
            .forEach(
                throwConsumer(
                    i -> subscribers.add(
                        dispatchQueue
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
        for (int i = 0; i < rand; i++) {
            dispatchQueue.emit(new TestDispatch(prefix + "_" + IdUtils.create(), i));
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
    void errorProcessing() throws QueueException, InterruptedException {
        String prefix = this.keyPrefix();

        CountDownLatch countDownLatch = new CountDownLatch(1);
        List<Integer> list = Collections.synchronizedList(new ArrayList<>());

        var crashed = new AtomicBoolean(false);

        QueueSubscriber<TestDispatch> subscriber = dispatchQueue
            .subscriber()
            .subscribe(e ->
            {
                if (e.getLeft().id == 2 && crashed.compareAndSet(false, true)) {
                    countDownLatch.countDown();
                    throw new RuntimeException("Boom");
                }

                list.add(e.getLeft().id);
                if (crashed.get()) {
                    fail("The consumer should not process the message after an error");
                }
            });

        dispatchQueue.emit(
            IntStream.range(1, 15)
                .boxed()
                .map(i -> new TestDispatch(prefix + "_" + IdUtils.create(), i))
                .toList()
        );

        boolean await = countDownLatch.await(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        subscriber.close();

        assertThat(await).isEqualTo(true);
        assertThat(countDownLatch.getCount()).isEqualTo(0L);
        assertThat(list).hasSize(1);
        assertThat(list.getFirst()).isEqualTo(1);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
            assertThat(noOpShutdownContext.isShutdownCalled()).as("shutdown() should have been called on processing error").isTrue()
        );

        // consume the remaining items from the queue
        CountDownLatch remaining = new CountDownLatch(3);
        subscriber = dispatchQueue
            .subscriber()
            .subscribe(e ->
            {
                remaining.countDown();
            });
        assertThat(remaining.await(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isEqualTo(true);
        subscriber.close();
    }

    @Test
    void pause() throws QueueException, InterruptedException {
        CountDownLatch countDownLatchFirst = new CountDownLatch(1);
        CountDownLatch countDownLatchSecond = new CountDownLatch(2);
        CountDownLatch countDownLatchOthers = new CountDownLatch(2);
        Collection<Pair<Instant, Integer>> list = Collections.synchronizedCollection(new ArrayList<>());

        QueueSubscriber<TestDispatch> subscriber = dispatchQueue
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

        // first round
        String prefix = this.keyPrefix();
        dispatchQueue.emit(new TestDispatch(prefix + "_" + IdUtils.create(), 1));

        boolean await1 = countDownLatchFirst.await(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        subscriber.pause();
        assertThat(await1).isTrue();

        // second round
        Instant resumeTime = Instant.now();
        subscriber.resume();

        dispatchQueue.emit(new TestDispatch(prefix + "_" + IdUtils.create(), 2));
        dispatchQueue.emit(new TestDispatch(prefix + "_" + IdUtils.create(), 3));

        boolean await2 = countDownLatchSecond.await(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        subscriber.pause();
        assertThat(await2).isTrue();

        // last round
        Instant resumeTime2 = Instant.now();
        subscriber.resume();

        dispatchQueue.emit(new TestDispatch(prefix + "_" + IdUtils.create(), 4));
        dispatchQueue.emit(new TestDispatch(prefix + "_" + IdUtils.create(), 5));

        boolean await3 = countDownLatchOthers.await(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        subscriber.close();

        assertThat(await3).isTrue();
        assertThat(list).hasSize(5);
        assertThat(list.stream().filter(i -> i.getLeft().isBefore(resumeTime)).count()).isEqualTo(1);
        assertThat(list.stream().filter(i -> i.getLeft().isAfter(resumeTime)).count()).isEqualTo(4);
        assertThat(list.stream().filter(i -> i.getLeft().isAfter(resumeTime2)).count()).isEqualTo(2);
    }

    @Test
    void queueMessageTooLarge() {
        char[] chars = new char[1100000];
        Arrays.fill(chars, 'a');

        AbstractDispatchQueueTest.TestDispatch message = new AbstractDispatchQueueTest.TestDispatch(this.keyPrefix() + "_" + IdUtils.create(), 1, new String(chars));

        var exception = assertThrows(QueueException.class, () -> dispatchQueue.emit(message));

        // the size is different on all runs, so we cannot assert on the exact message size
        assertThat(exception.getMessage()).contains("message of size");
        assertThat(exception.getMessage()).contains("has exceeded the configured limit of 1048576");
        assertThat(exception).isInstanceOf(MessageTooBigException.class);
    }

    @Test
    void shouldIgnoreRecordInConsumer() throws QueueException, InterruptedException {
        String prefix = this.keyPrefix();
        var record1 = new TestDispatch(prefix + "_" + IdUtils.create(), 1);
        var record2 = new TestDispatch(prefix + "_" + IdUtils.create(), 2);
        CountDownLatch countDownLatch = new CountDownLatch(1);
        Collection<Integer> list = Collections.synchronizedCollection(new ArrayList<>());
        ignoreExecutionService.setIgnoredQueueRecords(List.of(record1.key()));

        QueueSubscriber<TestDispatch> subscriber = dispatchQueue
            .subscriber()
            .subscribe(e ->
            {
                list.add(e.getLeft().id);
                countDownLatch.countDown();
            });

        dispatchQueue.emit(record1);
        dispatchQueue.emit(record2);

        boolean await = countDownLatch.await(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        subscriber.close();

        assertThat(await).isEqualTo(true);
        assertThat(countDownLatch.getCount()).isEqualTo(0L);
        assertThat(list).containsExactlyInAnyOrder(2);
    }

    @Test
    void shouldIgnoreRecordInBatchConsumer() throws QueueException, InterruptedException {
        String prefix = this.keyPrefix();
        var record1 = new TestDispatch(prefix + "_" + IdUtils.create(), 1);
        var record2 = new TestDispatch(prefix + "_" + IdUtils.create(), 2);
        CountDownLatch countDownLatch = new CountDownLatch(1);
        Collection<Integer> list = Collections.synchronizedCollection(new ArrayList<>());
        ignoreExecutionService.setIgnoredQueueRecords(List.of(record1.key()));

        QueueSubscriber<TestDispatch> subscriber = dispatchQueue
            .subscriber()
            .subscribeBatch(items ->
            {
                items.forEach(e ->
                {
                    list.add(e.getLeft().id);
                    countDownLatch.countDown();
                });
            });

        dispatchQueue.emit(record1);
        dispatchQueue.emit(record2);

        boolean await = countDownLatch.await(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        subscriber.close();

        assertThat(await).isEqualTo(true);
        assertThat(countDownLatch.getCount()).isEqualTo(0L);
        assertThat(list).containsExactlyInAnyOrder(2);
    }

    public record TestDispatch(String key, Integer id, String value) implements DispatchEvent {
        public TestDispatch(String key, Integer id) {
            this(key, id, null);
        }
    }
}
