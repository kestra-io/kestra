package io.kestra.jdbc.runner;

import java.sql.SQLException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.awaitility.Awaitility;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.kestra.core.contexts.KestraContext;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.property.Property;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.runners.Indexer;
import io.kestra.core.runners.Worker;
import io.kestra.core.runners.WorkerTaskResult;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import io.kestra.jdbc.JooqDSLContextWrapper;
import io.kestra.jdbc.repository.AbstractJdbcRepository;
import io.kestra.plugin.core.debug.Return;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import reactor.core.publisher.Flux;

import static io.kestra.core.utils.Rethrow.throwConsumer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

@KestraTest
abstract public class JdbcQueueTest {
    @Inject
    @Named(QueueFactoryInterface.FLOW_NAMED)
    protected QueueInterface<FlowInterface> flowQueue;

    @Inject
    protected JooqDSLContextWrapper dslContextWrapper;

    @Inject
    @Named(QueueFactoryInterface.WORKERTASKRESULT_NAMED)
    protected QueueInterface<WorkerTaskResult> workerTaskResultQueue;

    @Test
    void noGroup() throws InterruptedException, QueueException {
        CountDownLatch countDownLatch = new CountDownLatch(2);

        Flux<FlowInterface> receive = TestsUtils.receive(flowQueue, throwConsumer(either ->
        {
            FlowInterface flow = either.getLeft();
            if (flow.getNamespace().equals("io.kestra.f1")) {
                flowQueue.emit(builder("io.kestra.f2"));
            }

            countDownLatch.countDown();
        }));

        flowQueue.emit(builder("io.kestra.f1"));

        assertTrue(countDownLatch.await(5, TimeUnit.SECONDS));
        receive.blockLast();

        assertThat(countDownLatch.getCount()).isEqualTo(0L);
    }

    @Test
    void withGroup() throws InterruptedException, QueueException {
        CountDownLatch countDownLatch = new CountDownLatch(2);

        Flux<FlowInterface> receive = TestsUtils.receive(flowQueue, "consumer_group", throwConsumer(either ->
        {
            FlowInterface flow = either.getLeft();
            if (flow.getNamespace().equals("io.kestra.f1")) {
                flowQueue.emit("consumer_group", builder("io.kestra.f2"));
            }

            countDownLatch.countDown();
        }));

        flowQueue.emit("consumer_group", builder("io.kestra.f1"));

        assertTrue(countDownLatch.await(5, TimeUnit.SECONDS));
        receive.blockLast();

        assertThat(countDownLatch.getCount()).isEqualTo(0L);
    }

    @Test
    void withType() throws InterruptedException, QueueException {
        CountDownLatch countDownLatch = new CountDownLatch(2);
        Flux<FlowInterface> receive = TestsUtils.receive(flowQueue, Indexer.class, throwConsumer(either ->
        {
            FlowInterface flow = either.getLeft();
            if (flow.getNamespace().equals("io.kestra.f1")) {
                // second one
                flowQueue.emit(builder("io.kestra.f2"));
            }

            countDownLatch.countDown();
        }));

        // first one
        flowQueue.emit(builder("io.kestra.f1"));

        assertTrue(countDownLatch.await(5, TimeUnit.SECONDS));
        receive.blockLast();

        assertThat(countDownLatch.getCount()).isEqualTo(0L);
    }

    // FIXME
    @Test
    void withGroupAndType() throws InterruptedException, QueueException {
        CountDownLatch countDownLatch = new CountDownLatch(2);
        Flux<FlowInterface> receive = TestsUtils.receive(flowQueue, "consumer_group", Indexer.class, throwConsumer(either ->
        {
            FlowInterface flow = either.getLeft();
            if (flow.getNamespace().equals("io.kestra.f1")) {
                flowQueue.emit("consumer_group", builder("io.kestra.f2"));
            }

            countDownLatch.countDown();
        }));

        // first one
        flowQueue.emit("consumer_group", builder("io.kestra.f1"));

        assertTrue(countDownLatch.await(5, TimeUnit.SECONDS));
        receive.blockLast();

        assertThat(countDownLatch.getCount()).isEqualTo(0L);
    }

    @Test
    void shouldShutdownWhenConsumerFails() throws InterruptedException, QueueException {
        // Given: an unrecoverable failure while consuming. The poll thread must fail fast and shut the
        // server down (so it restarts and the message is redelivered), not silently die nor loop forever.
        // Spy the context so shutdown() is recorded instead of actually closing the test application context.
        KestraContext original = KestraContext.getContext();
        AtomicBoolean shutdownCalled = new AtomicBoolean(false);
        KestraContext spy = Mockito.spy(original);
        Mockito.doAnswer(invocation -> {
            shutdownCalled.set(true);
            return null;
        }).when(spy).shutdown();
        KestraContext.setContext(spy);

        try {
            CountDownLatch received = new CountDownLatch(1);
            TestsUtils.receive(flowQueue, throwConsumer(either -> {
                received.countDown();
                throw new RuntimeException("unrecoverable consumption failure");
            }));

            // When: a message is consumed and the consumer throws.
            flowQueue.emit(builder("io.kestra.f1"));
            assertTrue(received.await(5, TimeUnit.SECONDS));

            // Then: a server shutdown is initiated (fail fast).
            Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .untilTrue(shutdownCalled);
        } finally {
            KestraContext.setContext(original);
        }
    }

    @Test
    void receiveTransactionShouldHandOffAfterCommitOnceWhenTheClaimingTransactionIsRetried() throws InterruptedException, QueueException {
        // Given: a consumer whose claiming transaction is rolled back by a deadlock on its first attempt and
        // retried (JooqDSLContextWrapper). Side effects that cannot be rolled back must happen after the commit,
        // and only once, otherwise a worker job would be executed twice (kestra-ee#11945).
        String namespace = "io.kestra." + IdUtils.create().toLowerCase();
        AtomicInteger inTransactionCalls = new AtomicInteger();
        AtomicInteger afterCommitCalls = new AtomicInteger();
        CountDownLatch afterCommitLatch = new CountDownLatch(1);
        AtomicBoolean claimedWhenHandedOff = new AtomicBoolean(false);

        Runnable cancel = ((JdbcQueue<FlowInterface>) flowQueue).receiveTransaction(
            null,
            Worker.class,
            (dslContext, eithers) -> {
                if (eithers.stream().anyMatch(either -> either.isLeft() && either.getLeft().getNamespace().equals(namespace))) {
                    if (inTransactionCalls.incrementAndGet() == 1) {
                        throw new DataAccessException("simulated deadlock", new SQLException("Deadlock found when trying to get lock", "40001", 1213));
                    }
                }
            },
            eithers -> {
                if (eithers.stream().anyMatch(either -> either.isLeft() && either.getLeft().getNamespace().equals(namespace))) {
                    afterCommitCalls.incrementAndGet();
                    // the claim must already be visible to other connections, i.e. committed
                    claimedWhenHandedOff.set(isClaimed(namespace));
                    afterCommitLatch.countDown();
                }
            }
        );

        try {
            // When
            flowQueue.emit(builder(namespace));

            // Then: the message is delivered after the retried transaction committed, exactly once.
            assertTrue(afterCommitLatch.await(10, TimeUnit.SECONDS));
            // give a second delivery (the bug) the time to show up
            Thread.sleep(500);
            assertThat(inTransactionCalls.get()).isEqualTo(2);
            assertThat(afterCommitCalls.get()).isEqualTo(1);
            assertThat(claimedWhenHandedOff.get()).as("message handed off before its claim was committed").isTrue();
        } finally {
            cancel.run();
        }
    }

    /** Whether the queue row carrying the flow of this namespace is marked consumed, read from a new transaction. */
    private boolean isClaimed(String namespace) {
        List<Object> consumers = dslContextWrapper.transactionResult(configuration -> DSL.using(configuration)
            .select(AbstractJdbcRepository.field("consumers"))
            .from(DSL.table("queues"))
            .where(AbstractJdbcRepository.field("value").cast(String.class).like("%" + namespace + "%"))
            .fetch()
            .map(record -> record.get(0))
        );
        return !consumers.isEmpty() && consumers.stream().allMatch(Objects::nonNull);
    }

    private static FlowWithSource builder(String namespace) {
        return FlowWithSource.builder()
            .id(IdUtils.create())
            .namespace(namespace == null ? "kestra.test" : namespace)
            .tasks(Collections.singletonList(Return.builder().id("test").type(Return.class.getName()).format(Property.ofValue("test")).build()))
            .build();
    }
}
