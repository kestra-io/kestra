package io.kestra.executor.statemachine;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKilled;
import io.kestra.core.models.executions.ExecutionKilledExecution;
import io.kestra.core.models.flows.Concurrency;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.WorkerJobEvent;
import io.kestra.core.runners.WorkerTask;
import io.kestra.core.utils.IdUtils;
import io.kestra.executor.testkit.Executions;
import io.kestra.executor.testkit.ExecutorTestHarness;
import io.kestra.executor.testkit.Flows;
import io.kestra.executor.testkit.Results;
import io.kestra.executor.testkit.ScriptedWorker;
import io.kestra.executor.testkit.Trace;

import static io.kestra.executor.testkit.HarnessAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import reactor.core.publisher.Flux;

class KillReleasesConcurrencySlotTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant RETRY_DATE_PASSED = T0.plus(Duration.ofHours(2));

    private final ExecutorTestHarness harness = ExecutorTestHarness.create();

    @BeforeEach
    void noChildExecutionsToCascadeTheKillTo() {
        doReturn(Flux.empty()).when(harness.executionService()).killSubflowExecutions(any(), any());
        doReturn(List.of()).when(harness.executionService()).killLoopSubExecutions(any(), any());
    }

    @Test
    void killingTheSlotHolderWhoseTaskIsOnAWorkerReleasesTheSlotAndPopsTheQueue() {
        FlowWithSource flow = singleSlotFlow();
        Execution slotHolder = Executions.created(flow);
        Execution queued = Executions.created(flow);
        harness.step(slotHolder);
        harness.step(queued);
        assertThat(harness).as("before the kill").hasExecutionInState(slotHolder, State.Type.RUNNING).hasQueuedExactly(queued).hasRunning(flow, 1);

        Trace trace = harness.run(List.of(killRequest(slotHolder)), ScriptedWorker.killedFor(slotHolder, T0));

        assertKilledAndQueueDrained(flow, slotHolder, queued);
        assertThat(trace.emitted("execution").map(e -> e.as(Execution.class).getId()))
            .as("the queued execution re-entered the execution queue when the slot freed")
            .contains(queued.getId());
    }

    @Test
    void workerKilledResultArrivingBeforeTheKillRequestReleasesTheSlotOnce() {
        FlowWithSource flow = singleSlotFlow();
        Execution slotHolder = Executions.created(flow);
        Execution queued = Executions.created(flow);
        harness.step(slotHolder);
        harness.step(queued);

        harness.step(Results.killed(taskDispatchedFor(slotHolder), T0));
        harness.step(killRequest(slotHolder));
        harness.run(List.of(), ScriptedWorker.succeeding(T0));

        assertKilledAndQueueDrained(flow, slotHolder, queued);
    }

    @Test
    void killingTheSlotHolderWhileItWaitsForATaskRetryReleasesTheSlotAndPopsTheQueue() {
        FlowWithSource flow = singleSlotFlowWithHourlyRetry();
        Execution slotHolder = Executions.created(flow);
        Execution queued = Executions.created(flow);
        harness.clock().set(T0);
        harness.run(List.of(slotHolder, queued), ScriptedWorker.failing("a", T0));
        assertThat(harness).as("before the kill: the holder waits for its retry, on no worker").hasExecutionInState(slotHolder, State.Type.RETRYING).hasQueuedExactly(queued).hasPendingDelays(1);

        harness.run(List.of(killRequest(slotHolder)), ScriptedWorker.succeeding(T0));

        assertKilledAndQueueDrained(flow, slotHolder, queued);
    }

    @Test
    void anExpiredTaskRetryDoesNotRestartAnExecutionKilledWhileItWaited() {
        FlowWithSource flow = singleSlotFlowWithHourlyRetry();
        Execution killedWhileRetrying = Executions.created(flow);
        Execution queued = Executions.created(flow);
        harness.clock().set(T0);
        harness.run(List.of(killedWhileRetrying, queued), ScriptedWorker.failing("a", T0));
        harness.run(List.of(killRequest(killedWhileRetrying)), ScriptedWorker.succeeding(T0));
        assertThat(harness).as("before the retry date").hasExecutionInState(killedWhileRetrying, State.Type.KILLED).hasExecutionInState(queued, State.Type.SUCCESS).hasPendingDelays(1);

        List<Trace.Emission> onExpiry = harness.tickExecutionDelays(RETRY_DATE_PASSED);
        harness.run(List.of(), ScriptedWorker.succeeding(RETRY_DATE_PASSED));

        assertThat(harness).as("a kill is final: the retry of a killed execution never restarts its task").hasExecutionInState(killedWhileRetrying, State.Type.KILLED);
        assertThat(onExpiry).as("the expired retry sends nothing to a worker").extracting(Trace.Emission::queue).doesNotContain("workerJobEvent");
        assertThat(harness).as("the revived task would have re-held a slot; nothing does, and the delay is consumed").hasRunning(flow, 0).hasPendingDelays(0);
    }

    private void assertKilledAndQueueDrained(FlowWithSource flow, Execution slotHolder, Execution queued) {
        assertThat(harness).as("the kill ended the holder").hasExecutionInState(slotHolder, State.Type.KILLED);
        assertThat(harness).as("the kill freed the slot: the queued execution was popped").hasNothingQueued();
        assertThat(harness).as("the popped execution ran to the end").hasExecutionInState(queued, State.Type.SUCCESS);
        assertThat(harness).as("both executions are over, the slot is free again").hasRunning(flow, 0);
    }

    private FlowWithSource singleSlotFlow() {
        FlowWithSource flow = Flows.withConcurrency(Concurrency.builder().behavior(Concurrency.Behavior.QUEUE).limit(1).build(), Flows.log("a"));
        harness.registerFlow(flow);
        return flow;
    }

    private FlowWithSource singleSlotFlowWithHourlyRetry() {
        FlowWithSource flow = Flows.yaml("""
            id: retry-%s
            namespace: %s
            concurrency:
              behavior: QUEUE
              limit: 1
            tasks:
              - id: a
                type: io.kestra.plugin.core.log.Log
                message: hello
                retry:
                  type: constant
                  interval: PT1H
                  maxAttempts: 3
            """.formatted(IdUtils.create().toLowerCase(), Flows.NAMESPACE));
        harness.registerFlow(flow);
        return flow;
    }

    private WorkerTask taskDispatchedFor(Execution execution) {
        return harness.workerJobEventQueue().emittedMessages().stream()
            .map(event -> (WorkerTask) ((WorkerJobEvent) event).job())
            .filter(task -> task.getTaskRun().getExecutionId().equals(execution.getId()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no task was dispatched for execution " + execution.getId()));
    }

    private static ExecutionKilledExecution killRequest(Execution execution) {
        return ExecutionKilledExecution.builder()
            .state(ExecutionKilled.State.REQUESTED)
            .executionId(execution.getId())
            .isOnKillCascade(true)
            .tenantId(execution.getTenantId())
            .build();
    }
}
