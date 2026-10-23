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
import io.kestra.core.runners.ExecutionDelay;
import io.kestra.core.runners.WorkerTaskResult;
import io.kestra.executor.ExecutorContext;
import io.kestra.executor.testkit.Executions;
import io.kestra.executor.testkit.ExecutorTestHarness;
import io.kestra.executor.testkit.Flows;

import static io.kestra.executor.testkit.ExecutorContextAssert.assertThat;

/**
 * Layer-1 pause decision matrix over {@code ExecutorService#handlePausedDelay} (and the
 */
class PauseBehaviorTest {

    private static final String PAUSE_TASK_ID = "pause";
    private static final String ON_PAUSE_TASK_ID = "hello";
    private static final Duration PAUSE_DURATION = Duration.ofMinutes(5);

    private final ExecutorTestHarness harness = ExecutorTestHarness.create();

    // --- pauseDuration (delay)

    @Test
    void shouldPauseAndScheduleResumeDelayWhenPauseDurationSet() throws Exception {
        // Given: a flow whose first task is a Pause with a literal pauseDuration
        FlowWithSource flow = registered("""
            id: pause-delay
            namespace: io.kestra.tests

            tasks:
              - id: pause
                type: io.kestra.plugin.core.flow.Pause
                pauseDuration: PT5M
              - id: last
                type: io.kestra.plugin.core.log.Log
                message: after pause
            """);
        Instant before = Instant.now();

        // When: processing reaches the Pause task and the flowable transitions to RUNNING
        ExecutorContext paused = processUntilPaused(flow);

        assertThat(paused).as(
            "the execution is PAUSED and a RESUME_FLOW delay targets RUNNING (default behavior RESUME); the delay date is rendered from the wall-clock PAUSED history, so we only assert the lower bound, never the exact date"
        )
            .executionInState(State.Type.PAUSED)
            .hasTaskRunInState(PAUSE_TASK_ID, State.Type.PAUSED)
            .updatedFrom("handlePausedDelay")
            .hasExecutionDelay(ExecutionDelay.DelayType.RESUME_FLOW, State.Type.RUNNING)
            .hasExecutionDelayNotBefore(before.plus(PAUSE_DURATION))
            // downstream "last" task must not be scheduled while paused
            .hasNoWorkerTasks()
            .hasNoNexts();
    }

    @Test
    void shouldScheduleFailedDelayWhenPauseDurationWithFailBehavior() throws Exception {
        // Given: pauseDuration with behavior FAIL — the delay must target the behavior state
        FlowWithSource flow = registered("""
            id: pause-behavior-fail
            namespace: io.kestra.tests

            tasks:
              - id: pause
                type: io.kestra.plugin.core.flow.Pause
                pauseDuration: PT5M
                behavior: FAIL
              - id: last
                type: io.kestra.plugin.core.log.Log
                message: after pause
            """);

        // When
        ExecutorContext paused = processUntilPaused(flow);

        assertThat(paused).as("behavior FAIL maps to a FAILED target state on the RESUME_FLOW delay")
            .executionInState(State.Type.PAUSED)
            .hasExecutionDelay(ExecutionDelay.DelayType.RESUME_FLOW, State.Type.FAILED);
    }

    @Test
    void shouldScheduleCancelledDelayWhenPauseDurationWithCancelBehavior() throws Exception {
        // Given: pauseDuration with behavior CANCEL
        FlowWithSource flow = registered("""
            id: pause-behavior-cancel
            namespace: io.kestra.tests

            tasks:
              - id: pause
                type: io.kestra.plugin.core.flow.Pause
                pauseDuration: PT5M
                behavior: CANCEL
              - id: last
                type: io.kestra.plugin.core.log.Log
                message: after pause
            """);

        // When
        ExecutorContext paused = processUntilPaused(flow);

        assertThat(paused).as("behavior CANCEL maps to a CANCELLED target state")
            .executionInState(State.Type.PAUSED)
            .hasExecutionDelay(ExecutionDelay.DelayType.RESUME_FLOW, State.Type.CANCELLED);
    }

    // --- timeout (no pauseDuration)

    @Test
    void shouldScheduleFailedDelayWhenTimeoutSetWithoutPauseDuration() throws Exception {
        // Given: a Pause with only the standard task timeout — handlePausedDelay then uses
        // State.Type.fail(task) as the target state, NOT the behavior property
        FlowWithSource flow = registered("""
            id: pause-timeout
            namespace: io.kestra.tests

            tasks:
              - id: pause
                type: io.kestra.plugin.core.flow.Pause
                timeout: PT5M
              - id: last
                type: io.kestra.plugin.core.log.Log
                message: after pause
            """);
        Instant before = Instant.now();

        // When
        ExecutorContext paused = processUntilPaused(flow);

        assertThat(paused).as("without allowFailure the timeout target state is FAILED")
            .executionInState(State.Type.PAUSED)
            .hasTaskRunInState(PAUSE_TASK_ID, State.Type.PAUSED)
            .hasExecutionDelay(ExecutionDelay.DelayType.RESUME_FLOW, State.Type.FAILED)
            .hasExecutionDelayNotBefore(before.plus(PAUSE_DURATION));
    }

    @Test
    void shouldScheduleWarningDelayWhenTimeoutSetWithAllowFailure() throws Exception {
        // Given: timeout + allowFailure — State.Type.fail(task) downgrades FAILED to WARNING
        FlowWithSource flow = registered("""
            id: pause-timeout-allow-failure
            namespace: io.kestra.tests

            tasks:
              - id: pause
                type: io.kestra.plugin.core.flow.Pause
                timeout: PT5M
                allowFailure: true
              - id: last
                type: io.kestra.plugin.core.log.Log
                message: after pause
            """);

        // When
        ExecutorContext paused = processUntilPaused(flow);
        assertThat(paused)
            .executionInState(State.Type.PAUSED)
            .hasExecutionDelay(ExecutionDelay.DelayType.RESUME_FLOW, State.Type.WARNING);
    }

    // --- manual approval (no delay, no timeout)

    @Test
    void shouldPauseWithoutAnyDelayWhenManualApprovalPause() throws Exception {
        // Given: a bare Pause — waits forever for a manual resume
        FlowWithSource flow = registered("""
            id: pause-manual
            namespace: io.kestra.tests

            tasks:
              - id: pause
                type: io.kestra.plugin.core.flow.Pause
              - id: last
                type: io.kestra.plugin.core.log.Log
                message: after pause
            """);

        // When
        ExecutorContext paused = processUntilPaused(flow);

        assertThat(paused).as("PAUSED with NO ExecutionDelay — nothing will ever auto-resume it")
            .executionInState(State.Type.PAUSED)
            .hasTaskRunInState(PAUSE_TASK_ID, State.Type.PAUSED)
            .updatedFrom("handlePausedDelay")
            .hasNoExecutionDelays()
            .hasNoWorkerTasks()
            .hasNoNexts();
    }

    // --- onPause sub-task

    @Test
    void shouldEmitOnPauseWorkerTaskWhenOnPauseDefined() throws Exception {
        // Given: a manual-approval Pause carrying an onPause sub-task
        FlowWithSource flow = registered("""
            id: pause-on-pause
            namespace: io.kestra.tests

            tasks:
              - id: pause
                type: io.kestra.plugin.core.flow.Pause
                onPause:
                  id: hello
                  type: io.kestra.plugin.core.log.Log
                  message: Hello while paused
            """);

        // When
        ExecutorContext paused = processUntilPaused(flow);

        assertThat(paused).as(
            "the pausing cycle emits an ExecutorWorkerTask for the onPause task and adds its taskrun to the execution (handleFlowableTasks' Pause branch). Actually running the onPause task needs worker machinery, so the emitted worker task is the strongest observable truth at the executor boundary."
        )
            .executionInState(State.Type.PAUSED)
            .hasTaskRunInState(PAUSE_TASK_ID, State.Type.PAUSED)
            .updatedFrom("handlePauses")
            .hasWorkerTaskFor(ON_PAUSE_TASK_ID)
            .hasTaskRunInState(ON_PAUSE_TASK_ID, State.Type.CREATED)
            // no pauseDuration and no timeout: the onPause emission must not create a delay
            .hasNoExecutionDelays();
        Assertions.assertThat(paused.getWorkerTasks()).hasSize(1);
    }

    // --- saga plumbing

    private FlowWithSource registered(String yaml) {
        FlowWithSource flow = Flows.yaml(yaml);
        harness.registerFlow(flow);
        return flow;
    }

    /**
     * Drives the saga up to the pausing cycle: the first {@code process} stops when the Pause
     * flowable's CREATED worker task is emitted; we then transition it to RUNNING exactly like
     * the production {@code ExecutionEventMessageHandler} does for flowables (a RUNNING attempt
     * plus a RUNNING taskrun state) and process again — that cycle resolves the Pause state to
     * PAUSED and runs {@code handlePausedDelay}.
     */
    private ExecutorContext processUntilPaused(FlowWithSource flow) throws Exception {
        ExecutorContext created = harness.process(flow, Executions.created(flow));
        assertThat(created)
            .hasWorkerTaskFor(PAUSE_TASK_ID)
            .hasNoExecutionDelays();

        return harness.processResult(flow, created, runningFlowable(created.getWorkerTasks().getFirst()));
    }

    /**
     * The flowable RUNNING transition the production event handler performs before the
     * executor evaluates the flowable's own state.
     */
    private static WorkerTaskResult runningFlowable(ExecutorContext.ExecutorWorkerTask emitted) {
        TaskRun taskRun = emitted.workerTask().getTaskRun();
        return new WorkerTaskResult(
            taskRun
                .withAttempts(
                    List.of(
                        TaskRunAttempt.builder().state(new State().withState(State.Type.RUNNING)).build()
                    )
                )
                .withState(State.Type.RUNNING)
        );
    }
}
