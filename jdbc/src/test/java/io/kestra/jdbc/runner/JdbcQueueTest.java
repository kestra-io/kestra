package io.kestra.jdbc.runner;

import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.property.Property;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.runners.Indexer;
import io.kestra.core.runners.WorkerTaskResult;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.core.debug.Return;
import io.kestra.core.utils.IdUtils;
import io.kestra.jdbc.JdbcTestUtils;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static io.kestra.core.utils.Rethrow.throwConsumer;
import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
abstract public class JdbcQueueTest {
    @Inject
    @Named(QueueFactoryInterface.FLOW_NAMED)
    protected QueueInterface<FlowInterface> flowQueue;

    @Inject
    @Named(QueueFactoryInterface.WORKERTASKRESULT_NAMED)
    protected QueueInterface<WorkerTaskResult> workerTaskResultQueue;

    @Inject
    JdbcTestUtils jdbcTestUtils;

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

        countDownLatch.await(5, TimeUnit.SECONDS);
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

        countDownLatch.await(5, TimeUnit.SECONDS);
        receive.blockLast();

        assertThat(countDownLatch.getCount()).isEqualTo(0L);
    }

    @Test
    void withType() throws InterruptedException, QueueException {
        // first one
        flowQueue.emit(builder("io.kestra.f1"));

        CountDownLatch countDownLatch = new CountDownLatch(1);
        Flux<FlowInterface> receive = TestsUtils.receive(flowQueue, Indexer.class, either -> {
            countDownLatch.countDown();
        });

        countDownLatch.await(5, TimeUnit.SECONDS);

        assertThat(receive.blockLast().getNamespace()).isEqualTo("io.kestra.f1");

        // second one only
        flowQueue.emit(builder("io.kestra.f2"));

        CountDownLatch countDownLatch2 = new CountDownLatch(1);
        receive = TestsUtils.receive(flowQueue, Indexer.class, either -> {
            countDownLatch2.countDown();
        });
        countDownLatch2.await(5, TimeUnit.SECONDS);

        assertThat(receive.blockLast().getNamespace()).isEqualTo("io.kestra.f2");
    }

    @Test
    void withGroupAndType() throws InterruptedException, QueueException {
        // first one
        flowQueue.emit("consumer_group", builder("io.kestra.f1"));

        CountDownLatch countDownLatch = new CountDownLatch(1);
        Flux<FlowInterface> receive = TestsUtils.receive(flowQueue, "consumer_group", Indexer.class, either -> {
            countDownLatch.countDown();
        });

        countDownLatch.await(5, TimeUnit.SECONDS);

        assertThat(receive.blockLast().getNamespace()).isEqualTo("io.kestra.f1");

        // second one only
        flowQueue.emit("consumer_group", builder("io.kestra.f2"));

        CountDownLatch countDownLatch2 = new CountDownLatch(1);
        receive = TestsUtils.receive(flowQueue, "consumer_group", Indexer.class, either -> {
            countDownLatch2.countDown();
        });
        countDownLatch2.await(5, TimeUnit.SECONDS);

        assertThat(receive.blockLast().getNamespace()).isEqualTo("io.kestra.f2");
    }

    private static FlowWithSource builder(String namespace) {
        return FlowWithSource.builder()
            .id(IdUtils.create())
            .namespace(namespace == null ? "kestra.test" : namespace)
            .tasks(Collections.singletonList(Return.builder().id("test").type(Return.class.getName()).format(Property.of("test")).build()))
            .build();
    }

    @BeforeEach
    protected void init() {
        jdbcTestUtils.drop();
        jdbcTestUtils.migrate();
    }
}