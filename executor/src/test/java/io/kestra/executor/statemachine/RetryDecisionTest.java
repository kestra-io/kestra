package io.kestra.executor.statemachine;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.retrys.AbstractRetry;
import io.kestra.core.models.tasks.retrys.Constant;
import io.kestra.core.runners.ExecutionDelay;
import io.kestra.executor.ExecutorContext;
import io.kestra.executor.testkit.Executions;
import io.kestra.executor.testkit.ExecutorTestHarness;
import io.kestra.executor.testkit.Flows;
import io.kestra.executor.testkit.Results;
import io.kestra.plugin.core.log.Log;

import org.assertj.core.api.Assertions;

import static io.kestra.executor.testkit.ExecutorContextAssert.assertThat;

/**
 * Layer-1 saga: retry decision with behavior CREATE_NEW_EXECUTION, driven as
 * process → fail the emitted worker task → process again. Covers the decision logic behind
 * {@code AbstractRunnerRetryTest#retryNewExecution*} without runner, database or queues.
 */
class RetryDecisionTest {

    private static final Duration INTERVAL = Duration.ofMinutes(1);

    private ExecutorTestHarness harness;
    private FlowWithSource flow;

    @BeforeEach
    void setUp() {
        harness = ExecutorTestHarness.create();
        flow = Flows.of(
            Log.builder()
                .id("failing-task")
                .type(Log.class.getName())
                .message("boom")
                .retry(Constant.builder()
                    .interval(INTERVAL)
                    .maxAttempts(3)
                    .behavior(AbstractRetry.Behavior.CREATE_NEW_EXECUTION)
                    .build())
                .build()
        );
        harness.registerFlow(flow);
    }

    @Test
    void shouldScheduleNewExecutionDelayWhenTaskFailsUnderCreateNewExecution() throws Exception {
        // Given: a fresh execution — the executor must emit exactly one worker task
        Execution execution = Executions.created(flow);
        ExecutorContext cycle1 = harness.process(flow, execution);

        assertThat(cycle1)
            .hasWorkerTaskFor("failing-task")
            .hasNoExecutionDelays();

        // When: the "worker" fails the task; the attempt end date is our deterministic clock anchor
        Instant attemptEnd = Instant.parse("2026-07-06T10:00:00Z");
        ExecutorContext cycle2 = harness.processResult(
            flow,
            cycle1,
            Results.failed(cycle1.getWorkerTasks().getFirst(), attemptEnd)
        );

        // Then: CREATE_NEW_EXECUTION ⇒ a RESTART_FAILED_FLOW delay at attemptEnd + interval,
        // the taskrun marked RETRIED, and no other command leaks out of the accumulator
        assertThat(cycle2)
            .hasSingleExecutionDelay(delay -> {
                Assertions.assertThat(delay.getDelayType()).isEqualTo(ExecutionDelay.DelayType.RESTART_FAILED_FLOW);
                Assertions.assertThat(delay.getDate()).isEqualTo(attemptEnd.plus(INTERVAL));
                Assertions.assertThat(delay.getExecutionId()).isEqualTo(cycle2.getExecution().getId());
                Assertions.assertThat(delay.getState()).isEqualTo(State.Type.RUNNING);
            })
            .hasTaskRunInState("failing-task", State.Type.RETRIED)
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
    void shouldTerminateSuccessfullyWhenTaskSucceeds() throws Exception {
        // Given
        Execution execution = Executions.created(flow);
        ExecutorContext cycle1 = harness.process(flow, execution);
        assertThat(cycle1).hasWorkerTaskFor("failing-task");

        // When: the "worker" succeeds
        ExecutorContext cycle2 = harness.processResult(
            flow,
            cycle1,
            Results.success(cycle1.getWorkerTasks().getFirst(), Instant.parse("2026-07-06T10:00:00Z"))
        );

        // Then: no retry machinery involved, plain success
        assertThat(cycle2)
            .hasNoExecutionDelays()
            .hasTaskRunInState("failing-task", State.Type.SUCCESS)
            .executionInState(State.Type.SUCCESS);
    }
}
