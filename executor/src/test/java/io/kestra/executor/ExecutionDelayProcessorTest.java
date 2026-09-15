package io.kestra.executor;

import java.time.Instant;
import java.util.List;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import io.kestra.core.models.Label;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.ExecutionDelay;
import io.kestra.executor.testkit.Executions;
import io.kestra.executor.testkit.ExecutorTestHarness;
import io.kestra.executor.testkit.Flows;

import static io.kestra.executor.testkit.ExecutorContextAssert.assertThat;
import static io.kestra.executor.testkit.HarnessAssert.assertThat;

/**
 * Decision matrix for {@link ExecutionDelayProcessor} — what happens when a matured
 */
class ExecutionDelayProcessorTest {

    private static final Instant NOW = Instant.parse("2026-07-07T10:00:00Z");

    private final ExecutorTestHarness harness = ExecutorTestHarness.create();
    private final ExecutionDelayProcessor processor = harness.executionDelayProcessor();

    // --- the transactional-outbox invariant (kestra-io/kestra#17246)

    @Test
    void shouldPersistAndReturnContextsWithoutEmittingWhileProcessingExpiredDelays() {
        // Given: a FAILED execution whose CREATE_NEW_EXECUTION retry delay has matured
        FlowWithSource flow = Flows.of(Flows.log());
        harness.registerFlow(flow);
        Execution failed = Executions.created(flow).withState(State.Type.FAILED);
        harness.executionStateStore().save(failed);
        harness.executionDelayStateStore().save(restartFailedFlowDelay(failed, NOW));

        // When: the matured delay is processed (inside the delay store's transaction)
        List<ExecutorContext> toEmit = processor.processExpired(NOW);

        Assertions.assertThat(toEmit)
            .as("the replayed execution is minted and PERSISTED under its own id — the row a consumer will look up must be committed before any event about it can be observed").hasSize(1);
        Execution replayed = toEmit.getFirst().getExecution();
        Assertions.assertThat(replayed.getId()).isNotEqualTo(failed.getId());
        Assertions.assertThat(replayed.getState().getCurrent()).isEqualTo(State.Type.CREATED);
        Assertions.assertThat(harness.executionStateStore().findByIdWithoutAcl(replayed.getId())).isNotNull();

        // and NOTHING was emitted during processing: the processor has no queue access by
        // design — the caller (DefaultExecutor#executionDelayLoop) emits the returned contexts
        // only after processExpired() returns, i.e. after the transaction has committed
        assertThat(harness).emittedNothingElse();
        assertThat(harness).emittedNoKills();
        assertThat(harness).emittedNoLoopEvents();

        // and the delay is consumed
        Assertions.assertThat(harness.executionDelayStateStore().pending()).isEmpty();
    }

    // --- decision matrix per delay type

    @Test
    void shouldMintReplayWithNextAttemptWhenRestartFailedFlowDelayMatures() {
        // Given
        FlowWithSource flow = Flows.of(Flows.log());
        harness.registerFlow(flow);
        Execution failed = Executions.created(flow).withState(State.Type.FAILED);
        harness.executionStateStore().save(failed);
        harness.executionDelayStateStore().save(restartFailedFlowDelay(failed, NOW));

        // When
        List<ExecutorContext> toEmit = processor.processExpired(NOW);

        // Then: the new execution is a replay — fresh state, incremented attempt, REPLAY label
        Execution replayed = toEmit.getFirst().getExecution();
        Assertions.assertThat(replayed.getMetadata().getAttemptNumber())
            .isEqualTo(failed.getMetadata().getAttemptNumber() + 1);
        Assertions.assertThat(replayed.getLabels()).contains(new Label(Label.REPLAY, "true"));
        assertThat(toEmit.getFirst()).updatedFrom("retryFailedFlow");
    }

    @Test
    void shouldResumeDelayedStartWhenResumeFlowDelayHasNoTaskRun() {
        // Given: an execution scheduled for later (scheduleDate) — CREATED until the delay fires
        FlowWithSource flow = Flows.of(Flows.log());
        harness.registerFlow(flow);
        Execution scheduled = Executions.created(flow);
        harness.executionStateStore().save(scheduled);
        harness.executionDelayStateStore().save(
            ExecutionDelay.builder()
                .executionId(scheduled.getId())
                .date(NOW)
                .state(State.Type.RUNNING)
                .delayType(ExecutionDelay.DelayType.RESUME_FLOW)
                .build()
        );

        // When
        List<ExecutorContext> toEmit = processor.processExpired(NOW);

        Assertions.assertThat(toEmit).as("the execution transitions to the delay's target state, in place (same id)").hasSize(1);
        assertThat(toEmit.getFirst())
            .executionInState(State.Type.RUNNING)
            .updatedFrom("pausedRestart");
        Assertions.assertThat(toEmit.getFirst().getExecution().getId()).isEqualTo(scheduled.getId());
    }

    @Test
    void shouldNotResumeWhenExecutionIsBeingKilled() {
        // Given: KILLING is not terminated yet, but a matured delay must not race the kill
        FlowWithSource flow = Flows.of(Flows.log());
        harness.registerFlow(flow);
        Execution killing = Executions.created(flow).withState(State.Type.KILLING);
        harness.executionStateStore().save(killing);
        harness.executionDelayStateStore().save(
            ExecutionDelay.builder()
                .executionId(killing.getId())
                .date(NOW)
                .state(State.Type.RUNNING)
                .delayType(ExecutionDelay.DelayType.RESUME_FLOW)
                .build()
        );

        // When
        List<ExecutorContext> toEmit = processor.processExpired(NOW);

        Assertions.assertThat(toEmit).as("the context comes back untouched — no resume, execution still KILLING").hasSize(1);
        Assertions.assertThat(toEmit.getFirst().isExecutionUpdated()).isFalse();
        assertThat(toEmit.getFirst()).executionInState(State.Type.KILLING);
    }

    @Test
    void shouldNotResumeWhenExecutionAlreadyTerminated() {
        // Given: the execution terminated before its resume delay fired
        FlowWithSource flow = Flows.of(Flows.log());
        harness.registerFlow(flow);
        Execution done = Executions.created(flow).withState(State.Type.SUCCESS);
        harness.executionStateStore().save(done);
        harness.executionDelayStateStore().save(
            ExecutionDelay.builder()
                .executionId(done.getId())
                .date(NOW)
                .state(State.Type.RUNNING)
                .delayType(ExecutionDelay.DelayType.RESUME_FLOW)
                .build()
        );

        // When
        List<ExecutorContext> toEmit = processor.processExpired(NOW);
        Assertions.assertThat(toEmit).hasSize(1);
        Assertions.assertThat(toEmit.getFirst().isExecutionUpdated()).isFalse();
        assertThat(toEmit.getFirst()).executionInState(State.Type.SUCCESS);
    }

    @Test
    void shouldFailExecutionWhenFlowCannotBeFound() {
        // Given: a retry delay whose flow is not resolvable anymore
        FlowWithSource flow = Flows.of(Flows.log());
        Execution failed = Executions.created(flow).withState(State.Type.FAILED);
        harness.executionStateStore().save(failed);
        harness.executionDelayStateStore().save(restartFailedFlowDelay(failed, NOW));

        // When: the flow was never registered → FlowNotFoundException inside the callback
        List<ExecutorContext> toEmit = processor.processExpired(NOW);

        Assertions.assertThat(toEmit).as("the failure is captured on the context (handleFailedExecutionFromExecutor), never thrown through the store transaction").hasSize(1);
        assertThat(toEmit.getFirst()).executionInState(State.Type.FAILED);
    }

    @Test
    void shouldLeaveDelayPendingWhenNotYetDue() {
        // Given: a delay maturing in the future
        FlowWithSource flow = Flows.of(Flows.log());
        harness.registerFlow(flow);
        Execution failed = Executions.created(flow).withState(State.Type.FAILED);
        harness.executionStateStore().save(failed);
        harness.executionDelayStateStore().save(restartFailedFlowDelay(failed, NOW.plusSeconds(60)));

        // When
        List<ExecutorContext> toEmit = processor.processExpired(NOW);
        Assertions.assertThat(toEmit).isEmpty();
        Assertions.assertThat(harness.executionDelayStateStore().pending()).hasSize(1);
    }

    @Test
    void shouldSkipDelayWhenExecutionDoesNotExist() {
        // Given: a delay pointing at an execution the store has never seen (row not yet
        // committed — the "not ready for now" branch of the lock)
        harness.executionDelayStateStore().save(
            ExecutionDelay.builder()
                .executionId("missing-execution")
                .date(NOW)
                .state(State.Type.RUNNING)
                .delayType(ExecutionDelay.DelayType.RESUME_FLOW)
                .build()
        );

        // When
        List<ExecutorContext> toEmit = processor.processExpired(NOW);

        Assertions.assertThat(toEmit).as("nothing to emit").isEmpty();
    }

    // --- fixtures

    private static ExecutionDelay restartFailedFlowDelay(Execution execution, Instant date) {
        return ExecutionDelay.builder()
            .executionId(execution.getId())
            .date(date)
            .state(State.Type.CREATED)
            .delayType(ExecutionDelay.DelayType.RESTART_FAILED_FLOW)
            .build();
    }
}
