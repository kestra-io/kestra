package io.kestra.core.runners;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.services.ExecutionService;
import io.kestra.core.utils.Await;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Singleton
public class FlowConcurrencyCaseTest {
    @Inject
    private RunnerUtils runnerUtils;

    @Inject
    private ExecutionRepositoryInterface executionRepository;

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    protected QueueInterface<Execution> executionQueue;

    public void flowConcurrencyCancel() throws TimeoutException {
        Execution execution1 = runnerUtils.runOneUntilRunning(null, "io.kestra.tests", "flow-concurrency-cancel", null, null, Duration.ofSeconds(30));
        Execution execution2 = runnerUtils.runOne(null, "io.kestra.tests", "flow-concurrency-cancel");

        assertTrue(execution1.getState().isRunning());
        assertThat(execution2.getState().getCurrent(), is(State.Type.CANCELLED));

        Await.until(
            () -> {
                Execution execution = executionRepository.findById(execution1.getTenantId(), execution1.getId()).orElseThrow();
                return execution.getState().getCurrent() == State.Type.SUCCESS;
            },
            Duration.ofMillis(100),
            Duration.ofSeconds(1)
        );

        Await.until(
            () -> {
                Execution execution = executionRepository.findById(execution2.getTenantId(), execution2.getId()).orElseThrow();
                return execution.getState().getCurrent() == State.Type.CANCELLED;
            },
            Duration.ofMillis(100),
            Duration.ofSeconds(1)
        );
    }

    public void flowConcurrencyFail() throws TimeoutException {
        Execution execution1 = runnerUtils.runOneUntilRunning(null, "io.kestra.tests", "flow-concurrency-fail", null, null, Duration.ofSeconds(30));
        Execution execution2 = runnerUtils.runOne(null, "io.kestra.tests", "flow-concurrency-fail");

        assertTrue(execution1.getState().isRunning());
        assertThat(execution2.getState().getCurrent(), is(State.Type.FAILED));

        Await.until(
            () -> {
                Execution execution = executionRepository.findById(execution1.getTenantId(), execution1.getId()).orElseThrow();
                return execution.getState().getCurrent() == State.Type.SUCCESS;
            },
            Duration.ofMillis(100),
            Duration.ofSeconds(1)
        );

        Await.until(
            () -> {
                Execution execution = executionRepository.findById(execution2.getTenantId(), execution2.getId()).orElseThrow();
                return execution.getState().getCurrent() == State.Type.FAILED;
            },
            Duration.ofMillis(100),
            Duration.ofSeconds(1)
        );
    }

    public void flowConcurrencyQueue() throws TimeoutException {
        Execution execution1 = runnerUtils.runOneUntilRunning(null, "io.kestra.tests", "flow-concurrency-queue", null, null, Duration.ofSeconds(30));
        Flow flow = flowRepository
            .findById(null, "io.kestra.tests", "flow-concurrency-queue", Optional.empty())
            .orElseThrow();
        Execution execution2 = runnerUtils.newExecution(flow, null, null);
        executionQueue.emit(execution2);

        assertTrue(execution1.getState().isRunning());
        assertThat(execution2.getState().getCurrent(), is(State.Type.CREATED));

        Await.until(
            () -> {
                Execution execution = executionRepository.findById(execution1.getTenantId(), execution1.getId()).orElseThrow();
                return execution.getState().getCurrent() == State.Type.SUCCESS;
            },
            Duration.ofMillis(100),
            Duration.ofSeconds(1)
        );

        // soon it will be running as the previous execution is terminated, so it should go out of the queue
        Await.until(
            () -> {
                Execution execution = executionRepository.findById(execution2.getTenantId(), execution2.getId()).orElseThrow();
                return execution.getState().getCurrent() == State.Type.RUNNING;
            },
            Duration.ofMillis(100),
            Duration.ofSeconds(2)
        );

        Await.until(
            () -> {
                Execution execution = executionRepository.findById(execution2.getTenantId(), execution2.getId()).orElseThrow();
                return execution.getState().getCurrent() == State.Type.SUCCESS;
            },
            Duration.ofMillis(100),
            Duration.ofSeconds(1)
        );
    }
}
