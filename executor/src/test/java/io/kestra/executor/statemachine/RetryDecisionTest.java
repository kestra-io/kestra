package io.kestra.executor.statemachine;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.TaskRunAttempt;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.retrys.AbstractRetry;
import io.kestra.core.models.tasks.retrys.Constant;
import io.kestra.core.runners.ExecutionDelay;
import io.kestra.core.runners.WorkerTaskResult;
import io.kestra.executor.ExecutorContext;
import io.kestra.executor.testkit.Executions;
import io.kestra.executor.testkit.ExecutorTestHarness;
import io.kestra.executor.testkit.Flows;
import io.kestra.executor.testkit.Results;
import io.kestra.plugin.core.flow.AllowFailure;
import io.kestra.plugin.core.log.Log;

import static io.kestra.executor.testkit.ExecutorContextAssert.assertThat;

/**
 * Layer-1 retry decision matrix: behavior (CREATE_NEW_EXECUTION / RETRY_FAILED_TASK) × scope
 * (task retry / flow retry) × limits (attempts / duration), driven as explicit sagas —
 * process → fail the emitted worker task → assert the ExecutorContext command object.
 * Twins the decision logic behind AbstractRunnerRetryTest#retryNewExecution*,
 * #retryFailedTask*, #retryFailedFlow* (each of which boots a full StandAloneRunner × 5 backends).
 * No Micronaut, no database, no queues.
 */
class RetryDecisionTest {

    private static final Duration INTERVAL = Duration.ofMinutes(1);
    private static final Instant ATTEMPT_END = Instant.parse("2026-07-06T10:00:00Z");
    private static final String TASK_ID = "failing-task";

    private final ExecutorTestHarness harness = ExecutorTestHarness.create();

    // --- behavior CREATE_NEW_EXECUTION (task retry)

    @Test
    void shouldScheduleNewExecutionDelayWhenTaskFailsUnderCreateNewExecution() throws Exception {
        // Given: a fresh execution — the executor must emit exactly one worker task
        FlowWithSource flow = flowWithTaskRetry(AbstractRetry.Behavior.CREATE_NEW_EXECUTION, 3);
        ExecutorContext cycle1 = harness.process(flow, Executions.created(flow));

        assertThat(cycle1)
            .hasWorkerTaskFor(TASK_ID)
            .hasNoExecutionDelays();

        // When: the "worker" fails the task; the attempt end date is our deterministic clock anchor
        ExecutorContext cycle2 = harness.processResult(
            flow,
            cycle1,
            Results.failed(cycle1.getWorkerTasks().getFirst(), ATTEMPT_END)
        );

        // Then: a RESTART_FAILED_FLOW delay at attemptEnd + interval, the taskrun marked RETRIED,
        // and no other command leaks out of the accumulator
        assertThat(cycle2)
            .hasSingleExecutionDelay(delay ->
            {
                Assertions.assertThat(delay.getDelayType()).isEqualTo(ExecutionDelay.DelayType.RESTART_FAILED_FLOW);
                Assertions.assertThat(delay.getDate()).isEqualTo(ATTEMPT_END.plus(INTERVAL));
                Assertions.assertThat(delay.getExecutionId()).isEqualTo(cycle2.getExecution().getId());
                Assertions.assertThat(delay.getState()).isEqualTo(State.Type.RUNNING);
            })
            .hasTaskRunInState(TASK_ID, State.Type.RETRIED)
            .executionInState(State.Type.RETRIED)
            .updatedFrom("handleRetryTask")
            .hasNoWorkerTasks()
            .hasNoNexts()
            .hasNoSubflowExecutions();

        // side channels stayed clean — nothing escaped the command object
        Assertions.assertThat(harness.kills()).isEmpty();
        Assertions.assertThat(harness.loopEvents()).isEmpty();
    }

    @Test
    void shouldFailExecutionWhenCreateNewExecutionAttemptsExhausted() throws Exception {
        // Given: maxAttempts 1 while the execution is already attempt number 1
        FlowWithSource flow = flowWithTaskRetry(AbstractRetry.Behavior.CREATE_NEW_EXECUTION, 1);
        ExecutorContext cycle1 = harness.process(flow, Executions.created(flow));

        // When
        ExecutorContext cycle2 = harness.processResult(
            flow,
            cycle1,
            Results.failed(cycle1.getWorkerTasks().getFirst(), ATTEMPT_END)
        );

        // Then: no delay — the failure follows the normal FAILED path
        assertThat(cycle2)
            .hasNoExecutionDelays()
            .hasTaskRunInState(TASK_ID, State.Type.FAILED)
            .executionInState(State.Type.FAILED);
    }

    // --- behavior RETRY_FAILED_TASK (task retry)

    @Test
    void shouldScheduleTaskRestartDelayWhenTaskFailsUnderRetryFailedTask() throws Exception {
        // Given
        FlowWithSource flow = flowWithTaskRetry(AbstractRetry.Behavior.RETRY_FAILED_TASK, 3);
        ExecutorContext cycle1 = harness.process(flow, Executions.created(flow));
        assertThat(cycle1).hasWorkerTaskFor(TASK_ID);

        // When
        ExecutorContext cycle2 = harness.processResult(
            flow,
            cycle1,
            Results.failed(cycle1.getWorkerTasks().getFirst(), ATTEMPT_END)
        );

        // Then: the delay restarts the TASK in place — no new execution
        assertThat(cycle2)
            .hasSingleExecutionDelay(delay ->
            {
                Assertions.assertThat(delay.getDelayType()).isEqualTo(ExecutionDelay.DelayType.RESTART_FAILED_TASK);
                Assertions.assertThat(delay.getDate()).isEqualTo(ATTEMPT_END.plus(INTERVAL));
            })
            .hasTaskRunInState(TASK_ID, State.Type.RETRYING)
            .executionInState(State.Type.RETRYING)
            .updatedFrom("handleRetryTask")
            .hasNoWorkerTasks();
    }

    @Test
    void shouldFailExecutionWhenRetryFailedTaskAttemptsExhausted() throws Exception {
        // Given: maxAttempts 2 and a result already carrying 2 failed attempts
        FlowWithSource flow = flowWithTaskRetry(AbstractRetry.Behavior.RETRY_FAILED_TASK, 2);
        ExecutorContext cycle1 = harness.process(flow, Executions.created(flow));

        // When
        ExecutorContext cycle2 = harness.processResult(
            flow,
            cycle1,
            Results.failedAttempts(cycle1.getWorkerTasks().getFirst(), 2, ATTEMPT_END)
        );

        // Then
        assertThat(cycle2)
            .hasNoExecutionDelays()
            .hasTaskRunInState(TASK_ID, State.Type.FAILED)
            .executionInState(State.Type.FAILED);
    }

    @Test
    void shouldFailExecutionWhenRetryFailedTaskExceedsMaxDuration() throws Exception {
        // Given: the next retry date (attemptEnd + 10min) would exceed maxDuration (5min)
        FlowWithSource flow = Flows.of(
            failingTask(
                Constant.builder()
                    .interval(Duration.ofMinutes(10))
                    .maxDuration(Duration.ofMinutes(5))
                    .behavior(AbstractRetry.Behavior.RETRY_FAILED_TASK)
                    .build()
            )
        );
        harness.registerFlow(flow);
        ExecutorContext cycle1 = harness.process(flow, Executions.created(flow));

        // When
        ExecutorContext cycle2 = harness.processResult(
            flow,
            cycle1,
            Results.failed(cycle1.getWorkerTasks().getFirst(), ATTEMPT_END)
        );

        // Then
        assertThat(cycle2)
            .hasNoExecutionDelays()
            .executionInState(State.Type.FAILED);
    }

    // --- flow-level retry (task has none)

    @Test
    void shouldScheduleTaskRestartDelayWhenFlowRetryDefinedWithRetryFailedTask() throws Exception {
        // Given: the retry lives on the FLOW, the task has none
        FlowWithSource flow = flowWithFlowRetry(AbstractRetry.Behavior.RETRY_FAILED_TASK);
        ExecutorContext cycle1 = harness.process(flow, Executions.created(flow));
        assertThat(cycle1).hasWorkerTaskFor(TASK_ID);

        // When
        ExecutorContext cycle2 = harness.processResult(
            flow,
            cycle1,
            Results.failed(cycle1.getWorkerTasks().getFirst(), ATTEMPT_END)
        );

        // Then: flow-level RETRY_FAILED_TASK uses the taskrun attempt dates — exact arithmetic holds
        assertThat(cycle2)
            .hasSingleExecutionDelay(delay ->
            {
                Assertions.assertThat(delay.getDelayType()).isEqualTo(ExecutionDelay.DelayType.RESTART_FAILED_TASK);
                Assertions.assertThat(delay.getDate()).isEqualTo(ATTEMPT_END.plus(INTERVAL));
            })
            .hasTaskRunInState(TASK_ID, State.Type.RETRYING)
            .executionInState(State.Type.RETRYING);
    }

    @Test
    void shouldScheduleNewExecutionDelayWhenFlowRetryDefinedWithCreateNewExecution() throws Exception {
        // Given
        FlowWithSource flow = flowWithFlowRetry(AbstractRetry.Behavior.CREATE_NEW_EXECUTION);
        ExecutorContext cycle1 = harness.process(flow, Executions.created(flow));

        // When
        ExecutorContext cycle2 = harness.processResult(
            flow,
            cycle1,
            Results.failed(cycle1.getWorkerTasks().getFirst(), ATTEMPT_END)
        );

        // Then: flow-level CREATE_NEW_EXECUTION bases its date on the execution state history
        // (wall-clock stamped) — assert the decision, not the exact timestamp
        assertThat(cycle2)
            .hasSingleExecutionDelay(delay ->
            {
                Assertions.assertThat(delay.getDelayType()).isEqualTo(ExecutionDelay.DelayType.RESTART_FAILED_FLOW);
                Assertions.assertThat(delay.getDate()).isAfterOrEqualTo(ATTEMPT_END);
            })
            .hasTaskRunInState(TASK_ID, State.Type.RETRIED)
            .executionInState(State.Type.RETRIED);
    }

    // --- no retry involved

    @Test
    void shouldTerminateSuccessfullyWhenTaskSucceeds() throws Exception {
        // Given
        FlowWithSource flow = flowWithTaskRetry(AbstractRetry.Behavior.CREATE_NEW_EXECUTION, 3);
        ExecutorContext cycle1 = harness.process(flow, Executions.created(flow));
        assertThat(cycle1).hasWorkerTaskFor(TASK_ID);

        // When: the "worker" succeeds
        ExecutorContext cycle2 = harness.processResult(
            flow,
            cycle1,
            Results.success(cycle1.getWorkerTasks().getFirst(), ATTEMPT_END)
        );

        // Then: no retry machinery involved, plain success
        assertThat(cycle2)
            .hasNoExecutionDelays()
            .hasTaskRunInState(TASK_ID, State.Type.SUCCESS)
            .executionInState(State.Type.SUCCESS);
    }

    @Test
    void shouldFailExecutionWhenTaskFailsWithoutAnyRetry() throws Exception {
        // Given: no retry anywhere
        FlowWithSource flow = Flows.of(Log.builder().id(TASK_ID).type(Log.class.getName()).message("boom").build());
        harness.registerFlow(flow);
        ExecutorContext cycle1 = harness.process(flow, Executions.created(flow));

        // When
        ExecutorContext cycle2 = harness.processResult(
            flow,
            cycle1,
            Results.failed(cycle1.getWorkerTasks().getFirst(), ATTEMPT_END)
        );

        // Then
        assertThat(cycle2)
            .hasNoExecutionDelays()
            .executionInState(State.Type.FAILED);
    }

    // --- retry owned by a flowable with an errors branch (the branch gates the timer)

    @Test
    void shouldHoldRetryTimerUntilParentErrorsBranchCompletes() throws Exception {
        // Given: a child failing under an AllowFailure parent that owns both the retry and an
        // errors branch — the branch's side effects must be visible before the retry re-runs
        AllowFailure parent = AllowFailure.builder()
            .id("parent")
            .type(AllowFailure.class.getName())
            .tasks(List.of(Log.builder().id(TASK_ID).type(Log.class.getName()).message("boom").build()))
            .errors(List.of(Log.builder().id("on-error").type(Log.class.getName()).message("handling").build()))
            .retry(
                Constant.builder()
                    .interval(INTERVAL)
                    .maxAttempts(3)
                    .behavior(AbstractRetry.Behavior.RETRY_FAILED_TASK)
                    .build()
            )
            .build();
        FlowWithSource flow = Flows.of(parent);
        harness.registerFlow(flow);
        ExecutorContext cycle1 = harness.process(flow, Executions.created(flow));
        ExecutorContext cycle2 = startFlowable(flow, cycle1, "parent");
        assertThat(cycle2).hasWorkerTaskFor(TASK_ID);

        // When: the child fails — the parent's errors branch is resolved in the SAME executor
        // pass, so the pending check must see the just-resolved (not yet persisted) sibling
        ExecutorContext cycle3 = harness.processResult(
            flow,
            cycle2,
            Results.failed(emittedWorkerTask(cycle2, TASK_ID), ATTEMPT_END)
        );

        // Then: the retry timer is NOT armed while the errors branch is pending — the error
        // task is dispatched instead and the failed child stays FAILED, not RETRYING
        assertThat(cycle3)
            .hasNoExecutionDelays()
            .hasWorkerTaskFor("on-error")
            .hasTaskRunInState(TASK_ID, State.Type.FAILED);

        // When: the errors branch terminates
        ExecutorContext cycle4 = harness.processResult(
            flow,
            cycle3,
            Results.success(emittedWorkerTask(cycle3, "on-error"), ATTEMPT_END.plusSeconds(30))
        );

        // Then: only now is the task retry scheduled
        assertThat(cycle4)
            .hasSingleExecutionDelay(
                delay -> Assertions.assertThat(delay.getDelayType()).isEqualTo(ExecutionDelay.DelayType.RESTART_FAILED_TASK)
            )
            .hasTaskRunInState(TASK_ID, State.Type.RETRYING);
    }

    // --- fixtures

    /** Flowable parents are emitted as pseudo worker tasks; the event handler flips them RUNNING. */
    private ExecutorContext startFlowable(FlowWithSource flow, ExecutorContext previous, String taskId) throws Exception {
        TaskRun created = emittedWorkerTask(previous, taskId).workerTask().getTaskRun();
        TaskRun running = created
            .withAttempts(
                List.of(TaskRunAttempt.builder().state(new State().withState(State.Type.RUNNING)).build())
            )
            .withState(State.Type.RUNNING);
        return harness.processResult(flow, previous, new WorkerTaskResult(running));
    }

    private static ExecutorContext.ExecutorWorkerTask emittedWorkerTask(ExecutorContext context, String taskId) {
        return context.getWorkerTasks().stream()
            .filter(workerTask -> taskId.equals(workerTask.workerTask().getTaskRun().getTaskId()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no worker task emitted for <" + taskId + ">"));
    }

    private FlowWithSource flowWithTaskRetry(AbstractRetry.Behavior behavior, int maxAttempts) {
        FlowWithSource flow = Flows.of(
            failingTask(
                Constant.builder()
                    .interval(INTERVAL)
                    .maxAttempts(maxAttempts)
                    .behavior(behavior)
                    .build()
            )
        );
        harness.registerFlow(flow);
        return flow;
    }

    private FlowWithSource flowWithFlowRetry(AbstractRetry.Behavior behavior) {
        FlowWithSource flow = Flows.of(
            Flows.builder(Log.builder().id(TASK_ID).type(Log.class.getName()).message("boom").build())
                .retry(
                    Constant.builder()
                        .interval(INTERVAL)
                        .maxAttempts(3)
                        .behavior(behavior)
                        .build()
                )
                .build()
        );
        harness.registerFlow(flow);
        return flow;
    }

    private static Log failingTask(Constant retry) {
        return Log.builder()
            .id(TASK_ID)
            .type(Log.class.getName())
            .message("boom")
            .retry(retry)
            .build();
    }
}
