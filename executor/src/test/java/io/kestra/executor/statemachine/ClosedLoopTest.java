package io.kestra.executor.statemachine;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Concurrency;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.ExecutionEvent;
import io.kestra.core.runners.ExecutionEventType;
import io.kestra.executor.testkit.Executions;
import io.kestra.executor.testkit.ExecutorTestHarness;
import io.kestra.executor.testkit.Flows;
import io.kestra.executor.testkit.ScriptedWorker;
import io.kestra.executor.testkit.Trace;

import static io.kestra.executor.testkit.HarnessAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The production {@code DefaultExecutor} driven one queue delivery at a time: each test pins what one delivery
 * emits, and in which order, so the two-cycle handoff and the release-then-pop ordering stay observable.
 */
class ClosedLoopTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    private final ExecutorTestHarness harness = ExecutorTestHarness.create();

    @Test
    void shouldDispatchAndAnnounceInOneDeliveryWhenAnExecutionArrives() {
        // Given
        FlowWithSource flow = Flows.of(Flows.log("a"));
        harness.registerFlow(flow);
        Execution created = Executions.created(flow);

        // When: the execution is delivered on the execution queue
        List<Trace.Emission> emitted = harness.step(created);

        assertThat(harness.stateOf(created)).as("the handler dispatched the first task inside its lock, then the executor announced the update").isEqualTo(State.Type.RUNNING);
        assertThat(emitted).as("the worker job leaves first, then the update is announced").extracting(Trace.Emission::queue).containsExactly(Trace.WORKER_JOB_EVENT, Trace.EXECUTION_EVENT, Trace.FOLLOW_EXECUTION_EVENT);
        assertThat(emitted.get(1).as(ExecutionEvent.class).eventType()).as("the announced event is an UPDATED, not a TERMINATED").isEqualTo(ExecutionEventType.UPDATED);
    }

    @Test
    void shouldRunSequentialTasksToSuccessWhenDrivenThroughTheLoop() {
        // Given
        FlowWithSource flow = Flows.of(Flows.log("a"), Flows.log("b"));
        harness.registerFlow(flow);
        Execution created = Executions.created(flow);

        // When
        Trace trace = harness.run(created, ScriptedWorker.succeeding(T0));

        assertThat(trace.emitted(Trace.WORKER_JOB_EVENT)).as("both tasks went to a worker").hasSize(2);
        assertThat(harness).hasExecutionInState(created, State.Type.SUCCESS);

        List<Trace.Step> terminal = trace.steps().stream()
            .filter(step -> step.emitted(Trace.EXECUTION_EVENT).anyMatch(e -> e.as(ExecutionEvent.class).eventType() == ExecutionEventType.TERMINATED))
            .toList();
        assertThat(terminal).as("TERMINATED is published exactly once").hasSize(1);
        int terminalAt = trace.steps().indexOf(terminal.getFirst());
        assertThat(terminal.getFirst().queue())
            .as("two-cycle handoff: TERMINATED is announced by an execution-event delivery, not by the worker-result delivery")
            .isEqualTo(Trace.EXECUTION_EVENT);
        assertThat(trace.steps().get(terminalAt - 1).queue())
            .as("the delivery just before it is the worker result that completed the last task")
            .isEqualTo(Trace.WORKER_TASK_RESULT);
        assertThat(trace.steps().get(terminalAt - 1).emitted(Trace.EXECUTION_EVENT).map(e -> e.as(ExecutionEvent.class).eventType()))
            .as("the worker-result delivery merges the result and only announces UPDATED")
            .containsExactly(ExecutionEventType.UPDATED);
        assertThat(terminal.getFirst().emitted())
            .extracting(Trace.Emission::queue)
            .as("the terminating delivery says nothing else but the terminal trio")
            .containsExactly(Trace.EXECUTION_EVENT, Trace.FOLLOW_EXECUTION_EVENT, Trace.EXECUTION_STATISTIC, Trace.EXECUTION_TERMINATED);
        assertThat(trace.emitted(Trace.EXECUTION)).as("nothing to re-enqueue without a concurrency limit").isEmpty();
    }

    @Test
    void shouldRequeueTheNextExecutionBeforeAnnouncingTerminationWhenTheSlotHolderEnds() {
        // Given: one slot, two executions arriving back to back
        FlowWithSource flow = Flows.withConcurrency(Concurrency.builder().behavior(Concurrency.Behavior.QUEUE).limit(1).build(), Flows.log("a"));
        harness.registerFlow(flow);
        Execution first = Executions.created(flow);
        Execution second = Executions.created(flow);

        // When
        Trace trace = harness.run(List.of(first, second), ScriptedWorker.succeeding(T0));

        assertThat(harness.stateOf(first)).as("both ran to SUCCESS, one after the other, and the slot is free again").isEqualTo(State.Type.SUCCESS);
        assertThat(harness).hasExecutionInState(second, State.Type.SUCCESS);
        assertThat(harness).hasRunning(flow, 0);
        assertThat(harness).hasNothingQueued();

        Trace.Step releasing = trace.steps().stream()
            .filter(step -> step.emitted(Trace.EXECUTION).anyMatch(e -> e.as(Execution.class).getId().equals(second.getId())))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no delivery re-enqueued the queued execution"));
        Trace.Emission requeued = releasing.emitted(Trace.EXECUTION).findFirst().orElseThrow();
        Trace.Emission terminated = releasing.emitted(Trace.EXECUTION_EVENT).findFirst().orElseThrow();
        assertThat(terminated.as(ExecutionEvent.class).executionId()).as("the delivery that re-enqueued the second is the one that ended the first").isEqualTo(first.getId());
        assertThat(terminated.as(ExecutionEvent.class).eventType()).isEqualTo(ExecutionEventType.TERMINATED);
        assertThat(requeued.sequence())
            .as("release-then-pop is committed before any consumer can learn the first is over: the second is re-enqueued before TERMINATED is published")
            .isLessThan(terminated.sequence());
        assertThat(requeued.as(Execution.class).getState().getCurrent())
            .as("the popped execution is handed the freed slot in that same transaction, so it re-enters the queue already RUNNING")
            .isEqualTo(State.Type.RUNNING);
        assertThat(trace.deliveredFrom(Trace.EXECUTION).map(step -> ((Execution) step.message()).getId()))
            .as("the real execution-queue subscription picked the popped execution up again")
            .containsExactly(first.getId(), second.getId(), second.getId());
    }

    @Test
    void shouldWaitForTheDelayLoopAndTheClockWhenTheExecutionIsScheduled() {
        // Given: an execution scheduled one hour from now
        FlowWithSource flow = Flows.of(Flows.log("a"));
        harness.registerFlow(flow);
        Execution scheduled = Executions.created(flow).toBuilder().scheduleDate(T0.plus(Duration.ofHours(1))).build();

        // When: it arrives at T0
        harness.clock().set(T0);
        List<Trace.Emission> onArrival = harness.step(scheduled);

        assertThat(onArrival).as("nothing goes to a worker; a delay is parked").extracting(Trace.Emission::queue).doesNotContain(Trace.WORKER_JOB_EVENT);
        assertThat(harness).hasPendingDelays(1);

        // When: the delay loop ticks before and after the schedule date
        List<Trace.Emission> beforeTheDate = harness.tickExecutionDelays(T0.plus(Duration.ofMinutes(30)));
        List<Trace.Emission> afterTheDate = harness.tickExecutionDelays(T0.plus(Duration.ofHours(2)));
        harness.run(List.of(), ScriptedWorker.succeeding(T0.plus(Duration.ofHours(2))));

        assertThat(beforeTheDate).as("a tick before the schedule date changes nothing").isEmpty();
        assertThat(afterTheDate).as("a tick after the schedule date resumes the execution").extracting(Trace.Emission::queue).contains(Trace.EXECUTION_EVENT);
        assertThat(harness).as("the resumed execution ran to the end through the real subscriptions").hasExecutionInState(scheduled, State.Type.SUCCESS).hasPendingDelays(0);
    }

}
