package io.kestra.jdbc.runner;

import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueException;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.runners.AbstractRunnerConcurrencyTest;
import io.kestra.core.runners.ConcurrencyLimit;
import io.kestra.core.runners.TestRunnerUtils;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class JdbcConcurrencyRunnerTest extends AbstractRunnerConcurrencyTest {
    public static final String NAMESPACE = "io.kestra.tests";

    @Inject
    private AbstractJdbcConcurrencyLimitStorage concurrencyLimitStorage;

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Inject
    private ExecutionRepositoryInterface executionRepository;

    @Inject
    private TestRunnerUtils runnerUtils;

    @Test
    @LoadFlows(value = {"flows/valids/flow-concurrency-queue.yml"}, tenantId = "flow-concurrency-queued-protection")
    void flowConcurrencyQueuedProtection() throws QueueException, InterruptedException {
        Execution execution1 = runnerUtils.runOneUntilRunning("flow-concurrency-queued-protection", NAMESPACE, "flow-concurrency-queue", null, null, Duration.ofSeconds(30));
        assertThat(execution1.getState().isRunning()).isTrue();

        Flow flow = flowRepository
            .findById("flow-concurrency-queued-protection", NAMESPACE, "flow-concurrency-queue", Optional.empty())
            .orElseThrow();
        Execution execution2 = runnerUtils.emitAndAwaitExecution(e -> e.getState().getCurrent().equals(State.Type.QUEUED), Execution.newExecution(flow, null, null, Optional.empty()));
        assertThat(execution2.getState().getCurrent()).isEqualTo(State.Type.QUEUED);

        // manually update the concurrency count so that queued protection kicks in and no new execution would be popped
        ConcurrencyLimit concurrencyLimit = concurrencyLimitStorage.findById("flow-concurrency-queued-protection", NAMESPACE, "flow-concurrency-queue").orElseThrow();
        concurrencyLimit = concurrencyLimit.withRunning(concurrencyLimit.getRunning() + 1);
        concurrencyLimitStorage.update(concurrencyLimit);

        Execution executionResult1 = runnerUtils.awaitExecution(e -> e.getState().getCurrent().equals(State.Type.SUCCESS), execution1);
        assertThat(executionResult1.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        // we wait for a few ms and checked that the second execution is still queued
        Thread.sleep(500);
        Execution executionResult2 = executionRepository.findById("flow-concurrency-queued-protection", execution2.getId()).orElseThrow();
        assertThat(executionResult2.getState().getCurrent()).isEqualTo(State.Type.QUEUED);

        // we manually reset the concurrency count to avoid messing with any other tests
        concurrencyLimitStorage.update(concurrencyLimit.withRunning(concurrencyLimit.getRunning() - 1));
    }
}
