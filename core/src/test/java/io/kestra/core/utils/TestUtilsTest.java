package io.kestra.core.utils;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.runner.memory.MemoryQueue;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@MicronautTest
public class TestUtilsTest {
    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    private QueueInterface<Execution> executionMemoryQueue;

    @Test
    void receive() throws InterruptedException {
        MemoryQueue<Execution> cast = (MemoryQueue<Execution>) executionMemoryQueue;
        int subscribersCount = cast.getSubscribersCount();
        Flux<Execution> receive = TestsUtils.receive(executionMemoryQueue);
        assertThat(cast.getSubscribersCount(), is(subscribersCount + 1));
        receive.blockLast();
        assertThat(cast.getSubscribersCount(), is(subscribersCount));

        receive = TestsUtils.receive(executionMemoryQueue, either -> {
            if (either.getLeft().getId().equals("1")) {
                executionMemoryQueue.emit(Execution.builder().id("2").build());
            }
        });
        executionMemoryQueue.emit(Execution.builder().id("1").build());
        Thread.sleep(10000);
        assertThat(cast.getSubscribersCount(), is(subscribersCount + 1));
        List<Execution> executions = receive.collectList().block();
        assertThat(cast.getSubscribersCount(), is(subscribersCount));
        //assertThat(executions, is(List.of(Execution.builder().id("1").build(), Execution.builder().id("2").build())));

        assertThat(cast.getSubscribersCount(), is(subscribersCount));
        TestsUtils.receive(executionMemoryQueue, null, null, null, Duration.ofSeconds(5));
        assertThat(cast.getSubscribersCount(), is(subscribersCount + 1));
        Thread.sleep(10000);
        assertThat(cast.getSubscribersCount(), is(subscribersCount));
    }
}
