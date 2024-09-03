package io.kestra.core.runners;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.utils.TestsUtils;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@Singleton
public class FlowConcurrencyCaseTest {
    @Inject
    private RunnerUtils runnerUtils;

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    protected QueueInterface<Execution> executionQueue;

    public void flowConcurrencyCancel() throws TimeoutException, QueueException, InterruptedException {
        Execution execution1 = runnerUtils.runOneUntilRunning(null, "io.kestra.tests", "flow-concurrency-cancel", null, null, Duration.ofSeconds(30));
        Execution execution2 = runnerUtils.runOne(null, "io.kestra.tests", "flow-concurrency-cancel");

        assertThat(execution1.getState().isRunning(), is(true));
        assertThat(execution2.getState().getCurrent(), is(State.Type.CANCELLED));

        CountDownLatch latch1 = new CountDownLatch(1);

        Flux<Execution> receive = TestsUtils.receive(executionQueue, e -> {
            if (e.getLeft().getId().equals(execution1.getId())) {
                if (e.getLeft().getState().getCurrent() == State.Type.SUCCESS) {
                    latch1.countDown();
                }
            }

            // FIXME we should fail if we receive the cancel execution again but on Kafka it happens
        });

        latch1.await(1, TimeUnit.MINUTES);

        assertThat(receive.blockLast().getState().getCurrent(), is(State.Type.SUCCESS));
    }

    public void flowConcurrencyFail() throws TimeoutException, QueueException, InterruptedException {
        Execution execution1 = runnerUtils.runOneUntilRunning(null, "io.kestra.tests", "flow-concurrency-fail", null, null, Duration.ofSeconds(30));
        Execution execution2 = runnerUtils.runOne(null, "io.kestra.tests", "flow-concurrency-fail");

        assertThat(execution1.getState().isRunning(), is(true));
        assertThat(execution2.getState().getCurrent(), is(State.Type.FAILED));

        CountDownLatch latch1 = new CountDownLatch(1);

        Flux<Execution> receive = TestsUtils.receive(executionQueue, e -> {
            if (e.getLeft().getId().equals(execution1.getId())) {
                if (e.getLeft().getState().getCurrent() == State.Type.SUCCESS) {
                    latch1.countDown();
                }
            }

            // FIXME we should fail if we receive the cancel execution again but on Kafka it happens
        });

        latch1.await(1, TimeUnit.MINUTES);

        assertThat(receive.blockLast().getState().getCurrent(), is(State.Type.SUCCESS));
    }

    public void flowConcurrencyQueue() throws TimeoutException, QueueException, InterruptedException {
        Execution execution1 = runnerUtils.runOneUntilRunning(null, "io.kestra.tests", "flow-concurrency-queue", null, null, Duration.ofSeconds(30));
        Flow flow = flowRepository
            .findById(null, "io.kestra.tests", "flow-concurrency-queue", Optional.empty())
            .orElseThrow();
        Execution execution2 = Execution.newExecution(flow, null, null);
        executionQueue.emit(execution2);

        assertThat(execution1.getState().isRunning(), is(true));
        assertThat(execution2.getState().getCurrent(), is(State.Type.CREATED));

        var executionResult1  = new AtomicReference<Execution>();
        var executionResult2  = new AtomicReference<Execution>();

        CountDownLatch latch1 = new CountDownLatch(1);
        CountDownLatch latch2 = new CountDownLatch(1);
        CountDownLatch latch3 = new CountDownLatch(1);

        Flux<Execution> receive = TestsUtils.receive(executionQueue, e -> {
            if (e.getLeft().getId().equals(execution1.getId())) {
                executionResult1.set(e.getLeft());
                if (e.getLeft().getState().getCurrent() == State.Type.SUCCESS) {
                    latch1.countDown();
                }
            }

            if (e.getLeft().getId().equals(execution2.getId())) {
                executionResult2.set(e.getLeft());
                if (e.getLeft().getState().getCurrent() == State.Type.RUNNING) {
                    latch2.countDown();
                }
                if (e.getLeft().getState().getCurrent() == State.Type.SUCCESS) {
                    latch3.countDown();
                }
            }
        });

        latch1.await(1, TimeUnit.MINUTES);
        latch2.await(1, TimeUnit.MINUTES);
        latch3.await(1, TimeUnit.MINUTES);
        receive.blockLast();

        assertThat(executionResult1.get().getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(executionResult2.get().getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(executionResult2.get().getState().getHistories().getFirst().getState(), is(State.Type.CREATED));
        assertThat(executionResult2.get().getState().getHistories().get(1).getState(), is(State.Type.QUEUED));
        assertThat(executionResult2.get().getState().getHistories().get(2).getState(), is(State.Type.RUNNING));
    }

    public void flowConcurrencyQueuePause() throws TimeoutException, QueueException, InterruptedException {
        Execution execution1 = runnerUtils.runOneUntilRunning(null, "io.kestra.tests", "flow-concurrency-queue-pause", null, null, Duration.ofSeconds(30));
        Flow flow = flowRepository
            .findById(null, "io.kestra.tests", "flow-concurrency-queue-pause", Optional.empty())
            .orElseThrow();
        Execution execution2 = Execution.newExecution(flow, null, null);
        executionQueue.emit(execution2);

        assertThat(execution1.getState().isRunning(), is(true));
        assertThat(execution2.getState().getCurrent(), is(State.Type.CREATED));

        var executionResult1  = new AtomicReference<Execution>();
        var executionResult2  = new AtomicReference<Execution>();

        CountDownLatch latch1 = new CountDownLatch(1);
        CountDownLatch latch2 = new CountDownLatch(1);
        CountDownLatch latch3 = new CountDownLatch(1);

        Flux<Execution> receive = TestsUtils.receive(executionQueue, e -> {
            if (e.getLeft().getId().equals(execution1.getId())) {
                executionResult1.set(e.getLeft());
                if (e.getLeft().getState().getCurrent() == State.Type.SUCCESS) {
                    latch1.countDown();
                }
            }

            if (e.getLeft().getId().equals(execution2.getId())) {
                executionResult2.set(e.getLeft());
                if (e.getLeft().getState().getCurrent() == State.Type.RUNNING) {
                    latch2.countDown();
                }
                if (e.getLeft().getState().getCurrent() == State.Type.SUCCESS) {
                    latch3.countDown();
                }
            }
        });

        latch1.await(1, TimeUnit.MINUTES);
        latch2.await(1, TimeUnit.MINUTES);
        latch3.await(1, TimeUnit.MINUTES);
        receive.blockLast();

        assertThat(executionResult1.get().getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(executionResult2.get().getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(executionResult2.get().getState().getHistories().getFirst().getState(), is(State.Type.CREATED));
        assertThat(executionResult2.get().getState().getHistories().get(1).getState(), is(State.Type.QUEUED));
        assertThat(executionResult2.get().getState().getHistories().get(2).getState(), is(State.Type.RUNNING));
    }

    public void flowConcurrencyCancelPause() throws TimeoutException, QueueException, InterruptedException {
        Execution execution1 = runnerUtils.runOneUntilRunning(null, "io.kestra.tests", "flow-concurrency-cancel-pause", null, null, Duration.ofSeconds(30));
        Flow flow = flowRepository
            .findById(null, "io.kestra.tests", "flow-concurrency-cancel-pause", Optional.empty())
            .orElseThrow();
        Execution execution2 = Execution.newExecution(flow, null, null);
        executionQueue.emit(execution2);

        assertThat(execution1.getState().isRunning(), is(true));
        assertThat(execution2.getState().getCurrent(), is(State.Type.CREATED));

        var executionResult1  = new AtomicReference<Execution>();
        var executionResult2  = new AtomicReference<Execution>();

        CountDownLatch latch1 = new CountDownLatch(1);
        CountDownLatch latch2 = new CountDownLatch(1);

        Flux<Execution> receive = TestsUtils.receive(executionQueue, e -> {
            if (e.getLeft().getId().equals(execution1.getId())) {
                executionResult1.set(e.getLeft());
                if (e.getLeft().getState().getCurrent() == State.Type.SUCCESS) {
                    latch1.countDown();
                }
            }

            if (e.getLeft().getId().equals(execution2.getId())) {
                executionResult2.set(e.getLeft());
                if (e.getLeft().getState().getCurrent() == State.Type.CANCELLED) {
                    latch2.countDown();
                }
            }
        });

        latch1.await(1, TimeUnit.MINUTES);
        latch2.await(1, TimeUnit.MINUTES);
        receive.blockLast();

        assertThat(executionResult1.get().getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(executionResult2.get().getState().getCurrent(), is(State.Type.CANCELLED));
        assertThat(executionResult2.get().getState().getHistories().getFirst().getState(), is(State.Type.CREATED));
        assertThat(executionResult2.get().getState().getHistories().get(1).getState(), is(State.Type.CANCELLED));
    }
}
