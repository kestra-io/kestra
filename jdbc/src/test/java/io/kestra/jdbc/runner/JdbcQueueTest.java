package io.kestra.jdbc.runner;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.runners.Indexer;
import io.kestra.plugin.core.debug.Return;
import io.kestra.core.utils.IdUtils;
import io.kestra.jdbc.JdbcTestUtils;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@MicronautTest(transactional = false)
abstract public class JdbcQueueTest {
    @Inject
    @Named(QueueFactoryInterface.FLOW_NAMED)
    protected QueueInterface<Flow> flowQueue;

    @Inject
    JdbcTestUtils jdbcTestUtils;

    @Test
    void noGroup() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(2);

        flowQueue.receive(either -> {
            Flow flow = either.getLeft();
            if (flow.getNamespace().equals("io.kestra.f1")) {
                flowQueue.emit(builder("io.kestra.f2"));
            }

            countDownLatch.countDown();
        });

        flowQueue.emit(builder("io.kestra.f1"));

        countDownLatch.await(5, TimeUnit.SECONDS);

        assertThat(countDownLatch.getCount(), is(0L));
    }

    @Test
    void withGroup() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(2);

        flowQueue.receive("consumer_group", either -> {
            Flow flow = either.getLeft();
            if (flow.getNamespace().equals("io.kestra.f1")) {
                flowQueue.emit("consumer_group", builder("io.kestra.f2"));
            }

            countDownLatch.countDown();
        });

        flowQueue.emit("consumer_group", builder("io.kestra.f1"));

        countDownLatch.await(5, TimeUnit.SECONDS);

        assertThat(countDownLatch.getCount(), is(0L));
    }

    @Test
    void withType() throws InterruptedException {
        // first one
        flowQueue.emit(builder("io.kestra.f1"));

        AtomicReference<String> namespace = new AtomicReference<>();

        CountDownLatch countDownLatch = new CountDownLatch(1);
        flowQueue.receive(Indexer.class, either -> {
            Flow flow = either.getLeft();
            namespace.set(flow.getNamespace());
            countDownLatch.countDown();
        });

        countDownLatch.await(5, TimeUnit.SECONDS);

        assertThat(namespace.get(), is("io.kestra.f1"));

        // second one only
        flowQueue.emit(builder("io.kestra.f2"));

        CountDownLatch countDownLatch2 = new CountDownLatch(1);
        flowQueue.receive(Indexer.class, either -> {
            Flow flow = either.getLeft();
            namespace.set(flow.getNamespace());
            countDownLatch2.countDown();
        });
        countDownLatch2.await(5, TimeUnit.SECONDS);

        assertThat(namespace.get(), is("io.kestra.f2"));
    }

    @Test
    void withGroupAndType() throws InterruptedException {
        // first one
        flowQueue.emit("consumer_group", builder("io.kestra.f1"));

        AtomicReference<String> namespace = new AtomicReference<>();

        CountDownLatch countDownLatch = new CountDownLatch(1);
        flowQueue.receive("consumer_group", Indexer.class, either -> {
            Flow flow = either.getLeft();
            namespace.set(flow.getNamespace());
            countDownLatch.countDown();
        });

        countDownLatch.await(5, TimeUnit.SECONDS);

        assertThat(namespace.get(), is("io.kestra.f1"));

        // second one only
        flowQueue.emit("consumer_group", builder("io.kestra.f2"));

        CountDownLatch countDownLatch2 = new CountDownLatch(1);
        flowQueue.receive("consumer_group", Indexer.class, either -> {
            Flow flow = either.getLeft();
            namespace.set(flow.getNamespace());
            countDownLatch2.countDown();
        });
        countDownLatch2.await(5, TimeUnit.SECONDS);

        assertThat(namespace.get(), is("io.kestra.f2"));
    }

    private static Flow builder(String namespace) {
        return Flow.builder()
            .id(IdUtils.create())
            .namespace(namespace == null ? "kestra.test" : namespace)
            .tasks(Collections.singletonList(Return.builder().id("test").type(Return.class.getName()).format("test").build()))
            .build();
    }

    @BeforeEach
    protected void init() {
        jdbcTestUtils.drop();
        jdbcTestUtils.migrate();
    }
}