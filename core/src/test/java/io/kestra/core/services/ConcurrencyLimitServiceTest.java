package io.kestra.core.services;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.runners.ExecutionEvent;
import io.kestra.core.runners.ExecutionEventType;
import io.kestra.core.runners.RunnerUtils;
import io.kestra.core.utils.TestsUtils;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static io.kestra.core.utils.Rethrow.throwRunnable;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

@KestraTest(startRunner = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConcurrencyLimitServiceTest {
    private static final String TESTS_FLOW_NS = "io.kestra.tests";
    private static final String TENANT_ID = "main";

    @Inject
    private RunnerUtils runnerUtils;

    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    private QueueInterface<Execution> executionQueue;


    @Inject
    @Named(QueueFactoryInterface.EXECUTION_EVENT_NAMED)
    private QueueInterface<ExecutionEvent> executionEventQueue;
    @Inject
    private FlowRepositoryInterface flowRepositoryInterface;

    @Inject
    private ConcurrencyLimitService concurrencyLimitService;

    @Test
    @LoadFlows("flows/valids/flow-concurrency-queue.yml")
    void unqueueExecution() throws QueueException, TimeoutException, InterruptedException {
        // await for the executions to be terminated
        CountDownLatch terminated = new CountDownLatch(2);
        Flux<ExecutionEvent> receive = TestsUtils.receive(executionEventQueue, (either) -> {
            if (either.getLeft().flowId().equals("flow-concurrency-queue") && either.getLeft().eventType() == ExecutionEventType.TERMINATED) {
                terminated.countDown();
            }
        });

        // run a first flow so the second is queued
        Execution first = runnerUtils.runOneUntilRunning(TENANT_ID, TESTS_FLOW_NS, "flow-concurrency-queue");
        Execution result = runUntilQueued(TESTS_FLOW_NS, "flow-concurrency-queue");
        assertThat(result.getState().isQueued()).isTrue();

        Execution unqueued = concurrencyLimitService.unqueue(result, State.Type.RUNNING);
        assertThat(unqueued.getState().isRunning()).isTrue();
        executionQueue.emit(unqueued);

        assertTrue(terminated.await(10, TimeUnit.SECONDS));
        receive.blockLast();
    }



    private Execution runUntilQueued(String namespace, String flowId) throws TimeoutException, QueueException {
        return runUntilState(namespace, flowId, State.Type.QUEUED);
    }

    private Execution runUntilState(String namespace, String flowId, State.Type state) throws TimeoutException, QueueException {
        Execution execution = this.createExecution(namespace, flowId);
        return runnerUtils.awaitExecution(
            it -> execution.getId().equals(it.getId()) && it.getState().getCurrent() == state,
            throwRunnable(() -> this.executionQueue.emit(execution)),
            Duration.ofSeconds(1));
    }

    private Execution createExecution(String namespace, String flowId) {
        Flow flow = flowRepositoryInterface.findById(TENANT_ID, namespace, flowId).orElseThrow();
        return Execution.newExecution(flow, null);
    }
}