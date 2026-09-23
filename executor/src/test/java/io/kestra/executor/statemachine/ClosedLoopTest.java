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
 * The production {@code DefaultExecutor}, running: messages are delivered to its own subscribers,
 */
class ClosedLoopTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    private final ExecutorTestHarness harness = ExecutorTestHarness.create();

    @Test
    void oneDeliveryIsOneProductionStep() {
        // Given
        FlowWithSource flow = Flows.of(Flows.log("a"));
        harness.registerFlow(flow);
        Execution created = Executions.created(flow);

        // When: the execution is delivered on the execution queue
        List<Trace.Emission> emitted = harness.step(created);

        assertThat(harness.stateOf(created)).as("the handler dispatched the first task inside its lock, then the executor announced the update").isEqualTo(State.Type.RUNNING);
        assertThat(emitted).extracting(Trace.Emission::queue).containsExactly("workerJobEvent", "executionEvent", "followExecutionEvent");
        assertThat(emitted.get(1).as(ExecutionEvent.class).eventType()).isEqualTo(ExecutionEventType.UPDATED);
    }

    @Test
    void sequentialTasksRunToSuccessThroughTheLoop() {
        // Given
        FlowWithSource flow = Flows.of(Flows.log("a"), Flows.log("b"));
        harness.registerFlow(flow);
        Execution created = Executions.created(flow);

        // When
        Trace trace = harness.run(created, ScriptedWorker.succeeding(T0));

        assertThat(trace.emitted("workerJobEvent")).as("both tasks went to a worker").hasSize(2);
        assertThat(harness).hasExecutionInState(created, State.Type.SUCCESS);

        List<Trace.Step> terminal = trace.steps().stream()
            .filter(step -> step.emitted("executionEvent").anyMatch(e -> e.as(ExecutionEvent.class).eventType() == ExecutionEventType.TERMINATED))
            .toList();
        assertThat(terminal).as("TERMINATED is published exactly once").hasSize(1);
        int terminalAt = trace.steps().indexOf(terminal.getFirst());
        assertThat(terminal.getFirst().queue())
            .as("two-cycle handoff: TERMINATED is announced by an execution-event delivery, not by the worker-result delivery")
            .isEqualTo("executionEvent");
        assertThat(trace.steps().get(terminalAt - 1).queue())
            .as("the delivery just before it is the worker result that completed the last task")
            .isEqualTo("workerTaskResult");
        assertThat(trace.steps().get(terminalAt - 1).emitted("executionEvent").map(e -> e.as(ExecutionEvent.class).eventType()))
            .as("the worker-result delivery merges the result and only announces UPDATED")
            .containsExactly(ExecutionEventType.UPDATED);
        assertThat(terminal.getFirst().emitted())
            .extracting(Trace.Emission::queue)
            .as("the terminating delivery says nothing else but the terminal trio")
            .containsExactly("executionEvent", "followExecutionEvent", "executionStatistic", "executionTerminated");
        assertThat(trace.emitted("execution")).as("nothing to re-enqueue without a concurrency limit").isEmpty();
    }

    @Test
    void terminationReleasesTheSlotAndRequeuesBeforeAnnouncingTheEnd() {
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

        // the delivery that terminated the first re-enqueued the second BEFORE publishing its own TERMINATED:
        // release-then-pop is committed before any consumer can learn the first is over. The popped execution is
        // handed the freed slot inside that same transaction, so it re-enters the execution queue already RUNNING.
        Trace.Step releasing = trace.steps().stream()
            .filter(step -> step.emitted("execution").anyMatch(e -> e.as(Execution.class).getId().equals(second.getId())))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no delivery re-enqueued the queued execution"));
        Trace.Emission requeued = releasing.emitted("execution").findFirst().orElseThrow();
        Trace.Emission terminated = releasing.emitted("executionEvent").findFirst().orElseThrow();
        assertThat(requeued.sequence()).isLessThan(terminated.sequence());
        assertThat(terminated.as(ExecutionEvent.class).eventType()).isEqualTo(ExecutionEventType.TERMINATED);
        assertThat(terminated.as(ExecutionEvent.class).executionId()).isEqualTo(first.getId());
        assertThat(requeued.as(Execution.class).getState().getCurrent()).isEqualTo(State.Type.RUNNING);
        // and it was the real execution-queue subscription that picked the popped execution up again
        assertThat(trace.deliveredFrom("execution").map(step -> ((Execution) step.message()).getId())).containsExactly(first.getId(), second.getId(), second.getId());
    }

    @Test
    void scheduledExecutionWaitsForTheDelayLoopAndTheClock() {
        // Given: an execution scheduled one hour from now
        FlowWithSource flow = Flows.of(Flows.log("a"));
        harness.registerFlow(flow);
        Execution scheduled = Executions.created(flow).toBuilder().scheduleDate(T0.plus(Duration.ofHours(1))).build();

        // When: it arrives at T0
        harness.clock().set(T0);
        List<Trace.Emission> onArrival = harness.step(scheduled);

        assertThat(onArrival).as("nothing goes to a worker; a delay is parked").extracting(Trace.Emission::queue).doesNotContain("workerJobEvent");
        assertThat(harness.executionDelayStateStore().pending()).hasSize(1);

        // When: the delay loop ticks before the date — nothing happens; after the date — the task is dispatched
        assertThat(harness.tickExecutionDelays(T0.plus(Duration.ofMinutes(30)))).isEmpty();
        List<Trace.Emission> onResume = harness.tickExecutionDelays(T0.plus(Duration.ofHours(2)));
        assertThat(onResume).extracting(Trace.Emission::queue).contains("executionEvent");

        // and the loop finishes the run to SUCCESS through the real subscriptions
        harness.run(List.of(), ScriptedWorker.succeeding(T0.plus(Duration.ofHours(2))));
        assertThat(harness).hasExecutionInState(scheduled, State.Type.SUCCESS);
        assertThat(harness.executionDelayStateStore().pending()).isEmpty();
    }

}
