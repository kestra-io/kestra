package io.kestra.jdbc.runner;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.property.Property;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.runners.Indexer;
import io.kestra.core.runners.WorkerTaskResult;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.core.debug.Return;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static io.kestra.core.utils.Rethrow.throwConsumer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

@KestraTest
abstract public class JdbcQueueTest {
    @Inject
    @Named(QueueFactoryInterface.FLOW_NAMED)
    protected QueueInterface<FlowInterface> flowQueue;

    @Inject
    @Named(QueueFactoryInterface.WORKERTASKRESULT_NAMED)
    protected QueueInterface<WorkerTaskResult> workerTaskResultQueue;

    @Test
    void noGroup() throws InterruptedException, QueueException {
        CountDownLatch countDownLatch = new CountDownLatch(2);

        Flux<FlowInterface> receive = TestsUtils.receive(flowQueue, throwConsumer(either -> {
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

        Flux<FlowInterface> receive = TestsUtils.receive(flowQueue, "consumer_group", throwConsumer(either -> {
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
        Flux<FlowInterface> receive = TestsUtils.receive(flowQueue, Indexer.class, throwConsumer(either -> {
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
        Flux<FlowInterface> receive = TestsUtils.receive(flowQueue, "consumer_group", Indexer.class, throwConsumer(either -> {
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

    private static FlowWithSource builder(String namespace) {
        return FlowWithSource.builder()
            .id(IdUtils.create())
            .namespace(namespace == null ? "kestra.test" : namespace)
            .tasks(Collections.singletonList(Return.builder().id("test").type(Return.class.getName()).format(Property.ofValue("test")).build()))
            .build();
    }
}
