package io.kestra.core.runners;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.flows.State.History;
import io.kestra.core.models.flows.State.Type;
import io.kestra.core.queues.QueueException;
import io.kestra.core.repositories.ConcurrencyLimitRepositoryInterface;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.services.ExecutionService;
import io.kestra.core.utils.Await;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import static org.assertj.core.api.Assertions.assertThat;

@Singleton
public class FlowConcurrencyCaseTest {

    public static final String NAMESPACE = "io.kestra.tests";

    @Inject
    protected TestRunnerUtils runnerUtils;

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Inject
    private ExecutionService executionService;

    @Inject
    private ExecutionRepositoryInterface executionRepository;

    @Inject
    private ConcurrencyLimitRepositoryInterface concurrencyLimitRepository;

    public void flowConcurrencyCancel(String tenantId) throws TimeoutException, QueueException {
        Execution execution1 = runnerUtils.runOneUntilRunning(tenantId, NAMESPACE, "flow-concurrency-cancel", null, null, Duration.ofSeconds(30));
        try {
            List<Execution> shouldFailExecutions = List.of(
                runnerUtils.runOne(tenantId, NAMESPACE, "flow-concurrency-cancel"),
                runnerUtils.runOne(tenantId, NAMESPACE, "flow-concurrency-cancel")
            );
            assertThat(execution1.getState().isRunning()).isTrue();

            assertThat(shouldFailExecutions.stream().map(Execution::getState).map(State::getCurrent)).allMatch(Type.CANCELLED::equals);
        } finally {
            runnerUtils.killExecution(execution1);
        }
    }

    public void flowConcurrencyFail(String tenantId) throws TimeoutException, QueueException {
        Execution execution1 = runnerUtils.runOneUntilRunning(tenantId, NAMESPACE, "flow-concurrency-fail", null, null, Duration.ofSeconds(30));
        try {
            List<Execution> shouldFailExecutions = List.of(
                runnerUtils.runOne(tenantId, NAMESPACE, "flow-concurrency-fail"),
                runnerUtils.runOne(tenantId, NAMESPACE, "flow-concurrency-fail")
            );

            assertThat(execution1.getState().isRunning()).isTrue();
            assertThat(shouldFailExecutions.stream().map(Execution::getState).map(State::getCurrent)).allMatch(State.Type.FAILED::equals);
        } finally {
            runnerUtils.killExecution(execution1);
        }
    }

    public void flowConcurrencyQueue(String tenantId) throws QueueException {
        Execution execution1 = runnerUtils.runOneUntilRunning(tenantId, NAMESPACE, "flow-concurrency-queue", null, null, Duration.ofSeconds(30));
        Flow flow = flowRepository
            .findById(tenantId, NAMESPACE, "flow-concurrency-queue", Optional.empty())
            .orElseThrow();
        Execution execution2 = Execution.newExecution(flow, null, null, Optional.empty());
        Execution executionResult2 = runnerUtils.emitAndAwaitExecution(e -> e.getState().getCurrent().equals(Type.SUCCESS), execution2);
        Execution executionResult1 = runnerUtils.awaitExecution(e -> e.getState().getCurrent().equals(Type.SUCCESS), execution1);

        assertThat(execution1.getState().isRunning()).isTrue();
        assertThat(execution2.getState().getCurrent()).isEqualTo(State.Type.CREATED);

        assertThat(executionResult1.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(executionResult2.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(executionResult2.getState().getHistories().getFirst().getState()).isEqualTo(State.Type.CREATED);
        assertThat(executionResult2.getState().getHistories().get(1).getState()).isEqualTo(State.Type.QUEUED);
        assertThat(executionResult2.getState().getHistories().get(2).getState()).isEqualTo(State.Type.RUNNING);
    }

    public void flowConcurrencyQueuePause(String tenantId) throws QueueException {
        Execution execution1 = runnerUtils.runOneUntilPaused(tenantId, NAMESPACE, "flow-concurrency-queue-pause");
        Flow flow = flowRepository
            .findById(tenantId, NAMESPACE, "flow-concurrency-queue-pause", Optional.empty())
            .orElseThrow();
        Execution execution2 = Execution.newExecution(flow, null, null, Optional.empty());
        Execution secondExecutionResult = runnerUtils.emitAndAwaitExecution(e -> e.getState().getCurrent().equals(Type.SUCCESS), execution2);
        Execution firstExecutionResult = runnerUtils.awaitExecution(e -> e.getState().getCurrent().equals(Type.SUCCESS), execution1);

        assertThat(firstExecutionResult.getId()).isEqualTo(execution1.getId());
        assertThat(firstExecutionResult.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(secondExecutionResult.getId()).isEqualTo(execution2.getId());
        assertThat(secondExecutionResult.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(secondExecutionResult.getState().getHistories().getFirst().getState()).isEqualTo(State.Type.CREATED);
        assertThat(secondExecutionResult.getState().getHistories().get(1).getState()).isEqualTo(State.Type.QUEUED);
        assertThat(secondExecutionResult.getState().getHistories().get(2).getState()).isEqualTo(State.Type.RUNNING);
    }

    public void flowConcurrencyCancelPause(String tenantId) throws QueueException {
        Execution execution1 = runnerUtils.runOneUntilPaused(tenantId, NAMESPACE, "flow-concurrency-cancel-pause");
        Flow flow = flowRepository
            .findById(tenantId, NAMESPACE, "flow-concurrency-cancel-pause", Optional.empty())
            .orElseThrow();
        Execution execution2 = Execution.newExecution(flow, null, null, Optional.empty());
        Execution secondExecutionResult = runnerUtils.emitAndAwaitExecution(e -> e.getState().getCurrent().equals(Type.CANCELLED), execution2);
        Execution firstExecutionResult = runnerUtils.awaitExecution(e -> e.getState().getCurrent().equals(Type.SUCCESS), execution1);

        assertThat(firstExecutionResult.getId()).isEqualTo(execution1.getId());
        assertThat(firstExecutionResult.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(secondExecutionResult.getId()).isEqualTo(execution2.getId());
        assertThat(secondExecutionResult.getState().getCurrent()).isEqualTo(State.Type.CANCELLED);
        assertThat(secondExecutionResult.getState().getHistories().getFirst().getState()).isEqualTo(State.Type.CREATED);
        assertThat(secondExecutionResult.getState().getHistories().get(1).getState()).isEqualTo(State.Type.CANCELLED);
    }

    public void flowConcurrencyQueueRestarted(String tenantId) throws Exception {
        Execution execution1 = runnerUtils.runOneUntilRunning(
            tenantId, NAMESPACE,
            "flow-concurrency-queue-fail", null, null, Duration.ofSeconds(30)
        );
        Flow flow = flowRepository
            .findById(tenantId, NAMESPACE, "flow-concurrency-queue-fail", Optional.empty())
            .orElseThrow();
        Execution execution2 = Execution.newExecution(flow, null, null, Optional.empty());
        runnerUtils.emitAndAwaitExecution(e -> e.getState().getCurrent().equals(Type.RUNNING), execution2);

        // here the first fail and the second is now running.
        // we restart the first one, it should be queued then fail again.
        Execution failedExecution = runnerUtils.awaitExecution(e -> e.getState().getCurrent().equals(Type.FAILED), execution1);
        Execution restarted = executionService.restart(failedExecution, flow, null);
        Execution executionResult1 = runnerUtils.restartExecution(
            e -> e.getState().getHistories().stream().anyMatch(history -> history.getState() == Type.RESTARTED) && e.getState().getCurrent().equals(Type.FAILED),
            restarted
        );
        Execution executionResult2 = runnerUtils.awaitExecution(e -> e.getState().getCurrent().equals(Type.FAILED), execution2);

        assertThat(executionResult1.getState().getCurrent()).isEqualTo(Type.FAILED);
        // it should have been queued after restarted
        List<Type> stateList = executionResult1.getState().getHistories().stream().map(History::getState).toList();
        assertThat(stateList).contains(Type.RESTARTED);
        assertThat(stateList).contains(Type.QUEUED);
        assertThat(executionResult2.getState().getCurrent()).isEqualTo(Type.FAILED);
        assertThat(executionResult2.getState().getHistories().getFirst().getState()).isEqualTo(State.Type.CREATED);
        assertThat(executionResult2.getState().getHistories().get(1).getState()).isEqualTo(State.Type.QUEUED);
        assertThat(executionResult2.getState().getHistories().get(2).getState()).isEqualTo(State.Type.RUNNING);
    }

    public void flowConcurrencyQueueAfterExecution(String tenantId) throws QueueException {
        Execution execution1 = runnerUtils.runOneUntilRunning(tenantId, NAMESPACE, "flow-concurrency-queue-after-execution", null, null, Duration.ofSeconds(30));
        Flow flow = flowRepository
            .findById(tenantId, NAMESPACE, "flow-concurrency-queue-after-execution", Optional.empty())
            .orElseThrow();
        Execution execution2 = Execution.newExecution(flow, null, null, Optional.empty());
        Execution executionResult2 = runnerUtils.emitAndAwaitExecution(e -> e.getState().getCurrent().equals(Type.SUCCESS), execution2);
        Execution executionResult1 = runnerUtils.awaitExecution(e -> e.getState().getCurrent().equals(Type.SUCCESS), execution1);

        assertThat(executionResult1.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(executionResult2.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(executionResult2.getState().getHistories().getFirst().getState()).isEqualTo(State.Type.CREATED);
        assertThat(executionResult2.getState().getHistories().get(1).getState()).isEqualTo(State.Type.QUEUED);
        assertThat(executionResult2.getState().getHistories().get(2).getState()).isEqualTo(State.Type.RUNNING);
    }

    public void flowConcurrencySubflow(String tenantId) throws TimeoutException, QueueException {
        runnerUtils.runOneUntilRunning(tenantId, NAMESPACE, "flow-concurrency-subflow", null, null, Duration.ofSeconds(30));
        runnerUtils.runOne(tenantId, NAMESPACE, "flow-concurrency-subflow");

        List<Execution> subFlowExecs = runnerUtils.awaitFlowExecutionNumber(2, tenantId, NAMESPACE, "flow-concurrency-cancel");
        assertThat(subFlowExecs).extracting(e -> e.getState().getCurrent()).containsExactlyInAnyOrder(Type.SUCCESS, Type.CANCELLED);

        // run another execution to be sure that everything works (purge is correctly done)
        Execution execution3 = runnerUtils.runOne(tenantId, NAMESPACE, "flow-concurrency-subflow");
        assertThat(execution3.getState().getCurrent()).isEqualTo(Type.SUCCESS);
        runnerUtils.awaitFlowExecution(e -> e.getState().getCurrent().equals(Type.SUCCESS), tenantId, NAMESPACE, "flow-concurrency-cancel");
    }

    public void flowConcurrencyParallelSubflowKill(String tenantId) throws QueueException {
        Execution parent = runnerUtils.runOneUntilRunning(tenantId, NAMESPACE, "flow-concurrency-parallel-subflow-kill", null, null, Duration.ofSeconds(30));
        Execution queued = runnerUtils.awaitFlowExecution(e -> e.getState().isQueued(), tenantId, NAMESPACE, "flow-concurrency-parallel-subflow-kill-child");

        runnerUtils.killExecution(parent);
        Execution terminated = runnerUtils.awaitExecution(e -> e.getState().isTerminated(), queued);
        assertThat(terminated.getState().getCurrent()).isEqualTo(State.Type.KILLED);
        assertThat(terminated.getState().getHistories().stream().noneMatch(h -> h.getState() == Type.RUNNING)).isTrue();
        assertThat(terminated.getTaskRunList()).isNull();
    }

    public void flowConcurrencyKilled(String tenantId) throws QueueException {
        Flow flow = flowRepository
            .findById(tenantId, NAMESPACE, "flow-concurrency-queue-killed", Optional.empty())
            .orElseThrow();
        Execution execution1 = runnerUtils.runOneUntilRunning(tenantId, NAMESPACE, "flow-concurrency-queue-killed", null, null, Duration.ofSeconds(30));
        Execution execution2 = runnerUtils.emitAndAwaitExecution(e -> e.getState().getCurrent().equals(Type.QUEUED), Execution.newExecution(flow, null, null, Optional.empty()));
        Execution execution3 = runnerUtils.emitAndAwaitExecution(e -> e.getState().getCurrent().equals(Type.QUEUED), Execution.newExecution(flow, null, null, Optional.empty()));

        try {
            assertThat(execution1.getState().isRunning()).isTrue();
            assertThat(execution2.getState().getCurrent()).isEqualTo(Type.QUEUED);
            assertThat(execution3.getState().getCurrent()).isEqualTo(Type.QUEUED);

            // we kill execution 1, execution 2 should run but not execution 3
            Execution killed = runnerUtils.killExecution(execution1);
            assertThat(killed.getState().getCurrent()).isEqualTo(Type.KILLED);
            assertThat(killed.getState().getHistories().stream().anyMatch(h -> h.getState() == Type.RUNNING)).isTrue();

            // we now check that execution 2 is running
            Execution running = runnerUtils.awaitExecution(e -> e.getState().getCurrent().equals(Type.RUNNING), execution2);
            assertThat(running.getState().getCurrent()).isEqualTo(Type.RUNNING);

            // exec2 is running so the concurrency limit (1) is saturated — exec3 must stay queued
            Await.await()
                .during(Duration.ofMillis(100))
                .atMost(Duration.ofSeconds(5))
                .until(() -> executionRepository.findById(execution3.getTenantId(), execution3.getId())
                    .map(e -> e.getState().isQueued())
                    .orElse(false));
        } finally {
            runnerUtils.killExecution(execution2);
            runnerUtils.killExecution(execution3);
        }
    }

    public void flowConcurrencyQueueKilled(String tenantId) throws QueueException {
        Flow flow = flowRepository
            .findById(tenantId, NAMESPACE, "flow-concurrency-queue-killed", Optional.empty())
            .orElseThrow();
        Execution execution1 = runnerUtils.runOneUntilRunning(tenantId, NAMESPACE, "flow-concurrency-queue-killed", null, null, Duration.ofSeconds(30));
        Execution execution2 = runnerUtils.emitAndAwaitExecution(e -> e.getState().getCurrent().equals(Type.QUEUED), Execution.newExecution(flow, null, null, Optional.empty()));
        Execution execution3 = runnerUtils.emitAndAwaitExecution(e -> e.getState().getCurrent().equals(Type.QUEUED), Execution.newExecution(flow, null, null, Optional.empty()));

        try {
            assertThat(execution1.getState().isRunning()).isTrue();
            assertThat(execution2.getState().getCurrent()).isEqualTo(Type.QUEUED);
            assertThat(execution3.getState().getCurrent()).isEqualTo(Type.QUEUED);

            // we kill execution 2 (queued), execution 3 should not run
            Execution killed = runnerUtils.killExecution(execution2);
            assertThat(killed.getState().getCurrent()).isEqualTo(Type.KILLED);
            assertThat(killed.getState().getHistories().stream().noneMatch(h -> h.getState() == Type.RUNNING)).isTrue();

            // exec1 is still running, exec2 was killed from queue — exec3 must stay queued
            Await.await()
                .during(Duration.ofMillis(100))
                .atMost(Duration.ofSeconds(5))
                .until(() -> executionRepository.findById(execution3.getTenantId(), execution3.getId())
                    .map(e -> e.getState().isQueued())
                    .orElse(false));
        } finally {
            runnerUtils.killExecution(execution1);
            runnerUtils.killExecution(execution3);
        }
    }

    public void flowConcurrencyQueuedProtection(String tenantId) throws QueueException, InterruptedException {
        Execution execution1 = runnerUtils.runOneUntilRunning(tenantId, NAMESPACE, "flow-concurrency-queue", null, null, Duration.ofSeconds(30));
        assertThat(execution1.getState().isRunning()).isTrue();

        Flow flow = flowRepository
            .findById(tenantId, NAMESPACE, "flow-concurrency-queue", Optional.empty())
            .orElseThrow();
        Execution execution2 = runnerUtils.emitAndAwaitExecution(e -> e.getState().isQueued(), Execution.newExecution(flow, null, null, Optional.empty()));
        assertThat(execution2.getState().getCurrent()).isEqualTo(State.Type.QUEUED);

        // manually update the concurrency count so that queued protection kicks in and no new execution would be popped
        ConcurrencyLimit concurrencyLimit = concurrencyLimitRepository.findById(tenantId, NAMESPACE, "flow-concurrency-queue").orElseThrow();
        concurrencyLimit = concurrencyLimit.withRunning(concurrencyLimit.getRunning() + 1);
        concurrencyLimitRepository.update(concurrencyLimit);

        Execution executionResult1 = runnerUtils.awaitExecution(e -> e.getState().getCurrent().equals(State.Type.SUCCESS), execution1);
        assertThat(executionResult1.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        // we wait for a few ms and checked that the second execution is still queued
        Thread.sleep(500);
        Execution executionResult2 = runnerUtils.awaitExecution(e -> e.getState().isQueued(), execution2);
        assertThat(executionResult2.getState().getCurrent()).isEqualTo(State.Type.QUEUED);

        // we manually reset the concurrency count to avoid messing with any other tests
        concurrencyLimitRepository.update(concurrencyLimit.withRunning(concurrencyLimit.getRunning() - 1));
    }

    void flowConcurrencyScheduled(String tenantId) throws QueueException {
        Execution execution1 = runnerUtils.runOneUntilRunning(tenantId, NAMESPACE, "flow-concurrency-queue", null, null, Duration.ofSeconds(30));
        assertThat(execution1.getState().isRunning()).isTrue();

        Flow flow = flowRepository
            .findById(tenantId, NAMESPACE, "flow-concurrency-queue", Optional.empty())
            .orElseThrow();

        Execution scheduledExecution = Execution.newExecution(flow, null, null, Optional.empty())
            .withScheduleDate(java.time.Instant.now().plusSeconds(1));

        Execution execution2 = runnerUtils.emitAndAwaitExecution(
            e -> e.getState().getCurrent().equals(State.Type.QUEUED) || e.getState().getCurrent().equals(State.Type.RUNNING),
            scheduledExecution,
            Duration.ofSeconds(10)
        );

        assertThat(execution2.getState().getCurrent()).isEqualTo(State.Type.QUEUED);

        // cleanup
        runnerUtils.awaitExecution(e -> e.getState().getCurrent().equals(State.Type.SUCCESS), execution1);
        runnerUtils.awaitExecution(e -> e.getState().getCurrent().equals(State.Type.SUCCESS), execution2);
    }

}
