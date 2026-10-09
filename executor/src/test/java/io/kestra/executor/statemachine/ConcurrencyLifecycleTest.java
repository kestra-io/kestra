package io.kestra.executor.statemachine;

import java.util.List;
import java.util.Optional;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKilledExecution;
import io.kestra.core.models.flows.Concurrency;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.flows.sla.SLA;
import io.kestra.core.models.flows.sla.types.ExecutionAssertionSLA;
import io.kestra.core.runners.ExecutionEvent;
import io.kestra.core.runners.ExecutionEventType;
import io.kestra.executor.ExecutorContext;
import io.kestra.executor.testkit.Executions;
import io.kestra.executor.testkit.ExecutorTestHarness;
import io.kestra.executor.testkit.Flows;

import reactor.core.publisher.Flux;

import static io.kestra.executor.testkit.ExecutorContextAssert.assertThat;
import static io.kestra.executor.testkit.HarnessAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

/**
 * Layer-1 sagas of the concurrency-limit lifecycle through the real
 */
class ConcurrencyLifecycleTest {

    private final ExecutorTestHarness harness = ExecutorTestHarness.create();

    // --- claiming a slot

    @Test
    void shouldCountRunningExecutionWhenStartedUnderLimit() {
        // Given
        FlowWithSource flow = queueFlow(2);
        harness.registerFlow(flow);

        // When
        ExecutorContext started = harness.start(flow);

        assertThat(started).as("the execution runs and holds one slot; nothing is queued").executionInState(State.Type.RUNNING);
        assertThat(harness).hasRunning(flow, 1);
        assertThat(harness).hasNothingQueued();
    }

    @Test
    void shouldQueueExecutionWithoutClaimingSlotWhenLimitReached() {
        // Given: the single slot is taken
        FlowWithSource flow = queueFlow(1);
        harness.registerFlow(flow);
        harness.start(flow);

        // When: a second execution arrives
        Execution second = Executions.created(flow);
        ExecutorContext context = harness.handle(second);

        assertThat(context).as("it is parked QUEUED by the concurrency short-circuit — no slot claimed, no task run").executionInState(State.Type.QUEUED).updatedFrom("handleConcurrencyLimit");
        assertThat(context).hasNoTaskRuns();
        assertThat(harness).hasRunning(flow, 1);
        assertThat(harness).hasQueuedExactly(second);
    }

    @Test
    void shouldCancelExecutionWithoutClaimingSlotWhenLimitReached() {
        // Given
        FlowWithSource flow = flowWithConcurrency(Concurrency.Behavior.CANCEL, 1);
        harness.registerFlow(flow);
        harness.start(flow);

        // When
        Execution second = Executions.created(flow);
        ExecutorContext context = harness.handle(second);

        assertThat(context).as("CANCELLED terminal, counter untouched (it never ran), nothing queued").executionInState(State.Type.CANCELLED).updatedFrom("handleConcurrencyLimit");
        assertThat(harness).hasRunning(flow, 1);
        assertThat(harness).hasNothingQueued();
    }

    @Test
    void shouldFailExecutionWithoutClaimingSlotWhenLimitReached() {
        // Given
        FlowWithSource flow = flowWithConcurrency(Concurrency.Behavior.FAIL, 1);
        harness.registerFlow(flow);
        harness.start(flow);

        // When
        Execution second = Executions.created(flow);
        ExecutorContext context = harness.handle(second);
        assertThat(context).executionInState(State.Type.FAILED).updatedFrom("handleConcurrencyLimit");
        assertThat(harness).hasRunning(flow, 1);
        assertThat(harness).hasNothingQueued();
    }

    @Test
    void shouldFillEverySlotBeforeQueueing() {
        // Given: a limit of 2
        FlowWithSource flow = queueFlow(2);
        harness.registerFlow(flow);

        // When: two executions start
        ExecutorContext first = harness.start(flow);
        ExecutorContext second = harness.start(flow);

        assertThat(first).as("both run — the limit does not trip one short of the boundary").executionInState(State.Type.RUNNING);
        assertThat(second).executionInState(State.Type.RUNNING);
        assertThat(harness).hasRunning(flow, 2);

        // When: a third arrives
        Execution third = Executions.created(flow);
        ExecutorContext context = harness.handle(third);

        assertThat(context).as("exactly at the limit it queues").executionInState(State.Type.QUEUED).updatedFrom("handleConcurrencyLimit");
        assertThat(harness).hasRunning(flow, 2);
    }

    @Test
    void shouldKeepQueuedExecutionsInArrivalOrder() {
        // Given: the single slot is taken
        FlowWithSource flow = queueFlow(1);
        harness.registerFlow(flow);
        harness.start(flow);

        // When: two more executions arrive
        Execution second = Executions.created(flow);
        harness.handle(second);
        Execution third = Executions.created(flow);
        harness.handle(third);

        assertThat(harness).as("both park in arrival order — the order decrementAndPop replays them in — and still only one slot is held").hasQueuedExactly(second, third);
        assertThat(harness).hasRunning(flow, 1);
    }

    // --- how the counter is keyed

    @Test
    void shouldKeepCountersIndependentAcrossNamespaces() {
        // Given: two flows with the same id in different namespaces, each limited to 1
        Concurrency limitOne = Concurrency.builder().behavior(Concurrency.Behavior.QUEUE).limit(1).build();
        FlowWithSource flowA = Flows.of(Flows.builder(Flows.log()).id("same-id").concurrency(limitOne).build());
        FlowWithSource flowB = Flows.of(
            Flows.builder(Flows.log()).id("same-id").namespace(Flows.NAMESPACE + ".other").concurrency(limitOne).build()
        );
        harness.registerFlow(flowA).registerFlow(flowB);

        // When: both namespaces start an execution
        harness.start(flowA);
        ExecutorContext startedB = harness.start(flowB);

        assertThat(startedB).as("B runs — A's full slot does not bleed into B's counter").executionInState(State.Type.RUNNING);
        assertThat(harness).hasRunning(flowA, 1);
        assertThat(harness).hasRunning(flowB, 1);
        assertThat(harness).hasNothingQueued();
    }

    @Test
    void shouldKeepCountersIndependentAcrossTenants() {
        // Given: two flows with the same id AND namespace in different tenants, each limited to 1
        Concurrency limitOne = Concurrency.builder().behavior(Concurrency.Behavior.QUEUE).limit(1).build();
        FlowWithSource flowA = Flows.of(Flows.builder(Flows.log()).id("tenant-keyed").concurrency(limitOne).build());
        FlowWithSource flowB = Flows.of(
            Flows.builder(Flows.log()).id("tenant-keyed").tenantId("other-tenant").concurrency(limitOne).build()
        );
        harness.registerFlow(flowA).registerFlow(flowB);

        // When: both tenants start an execution
        harness.start(flowA);
        ExecutorContext startedB = harness.start(flowB);

        assertThat(startedB).as("B runs — a tenant's full slot never bleeds into another tenant's counter").executionInState(State.Type.RUNNING);
        assertThat(harness).hasRunning(flowA, 1);
        assertThat(harness).hasRunning(flowB, 1);
        assertThat(harness).hasNothingQueued();
    }

    @Test
    void shouldShareCounterAcrossFlowRevisions() {
        // Given: revision 1 holds the single slot
        Concurrency limitOne = Concurrency.builder().behavior(Concurrency.Behavior.QUEUE).limit(1).build();
        FlowWithSource revision1 = Flows.of(Flows.builder(Flows.log()).id("revisioned").concurrency(limitOne).build());
        harness.registerFlow(revision1);
        harness.start(revision1);

        // When: an execution of revision 2 of the same flow arrives
        FlowWithSource revision2 = Flows.of(
            Flows.builder(Flows.log()).id("revisioned").revision(2).concurrency(limitOne).build()
        );
        harness.registerFlow(revision2);
        Execution second = Executions.created(revision2);
        ExecutorContext context = harness.handle(second);

        assertThat(context).as("the counter is keyed without the revision — revision 2 queues behind revision 1").executionInState(State.Type.QUEUED);
        assertThat(harness).hasRunning(revision2, 1);
    }

    @Test
    void shouldApplyLoweredLimitFromLatestFlowDefinition() {
        // Given: two executions admitted under revision 1's limit of 3
        Concurrency limitThree = Concurrency.builder().behavior(Concurrency.Behavior.QUEUE).limit(3).build();
        FlowWithSource revision1 = Flows.of(Flows.builder(Flows.log()).id("lowered-limit").concurrency(limitThree).build());
        harness.registerFlow(revision1);
        harness.start(revision1);
        harness.start(revision1);
        assertThat(harness).hasRunning(revision1, 2);

        // When: revision 2 lowers the limit to 1 and a new execution arrives
        Concurrency limitOne = Concurrency.builder().behavior(Concurrency.Behavior.QUEUE).limit(1).build();
        FlowWithSource revision2 = Flows.of(
            Flows.builder(Flows.log()).id("lowered-limit").revision(2).concurrency(limitOne).build()
        );
        harness.registerFlow(revision2);
        Execution third = Executions.created(revision2);
        ExecutorContext context = harness.handle(third);

        assertThat(context)
            .as("the latest definition's limit governs — 2 running >= 1 queues the arrival even though both runs were admitted under the laxer revision, and no slot is reclaimed")
            .executionInState(State.Type.QUEUED).updatedFrom("handleConcurrencyLimit");
        assertThat(harness).hasRunning(revision2, 2);
        assertThat(harness).hasQueuedExactly(third);
    }

    // --- leaving the queue

    @Test
    void shouldRemoveKilledExecutionFromQueueWithoutReleasingASlot() {
        // Given: a queued execution behind a full slot
        FlowWithSource flow = queueFlow(1);
        harness.registerFlow(flow);
        harness.start(flow);
        Execution second = Executions.created(flow);
        harness.handle(second);
        assertThat(harness).hasQueuedCount(1);

        // killSubflowExecutions/killLoopSubExecutions query the execution repository on the real
        // ExecutionService; stub them to "no child executions"
        doReturn(Flux.empty()).when(harness.executionService()).killSubflowExecutions(any(), any());
        doReturn(List.of()).when(harness.executionService()).killLoopSubExecutions(any(), any());

        // When: the queued execution is killed
        var killed = harness.executionKilledExecutionMessageHandler().handle(
            ExecutionKilledExecution.builder()
                .tenantId(second.getTenantId())
                .executionId(second.getId())
                .executionState(State.Type.KILLED)
                .build()
        );

        Assertions.assertThat(killed)
            .as("it leaves the queued store so a freed slot can never restart it, and the running counter is untouched — a queued execution never claimed a slot, so its kill releases none")
            .isPresent();
        Assertions.assertThat(killed.get().getExecution().getState().getCurrent()).isEqualTo(State.Type.KILLED);
        assertThat(harness).hasNothingQueued();
        assertThat(harness).hasRunning(flow, 1);
    }

    // --- releasing a slot: claim through the handler, free through the release processor —
    // the exact pair DefaultExecutor#toExecution orchestrates. These sagas pin the seams a
    // namespace- or tenant-level concurrency limit would have to preserve: per-flow counter
    // keying, per-flow pop scope, and unlimited flows being invisible to the machinery.

    @Test
    void shouldDrainQueueInArrivalOrderAsEachSlotFrees() {
        // Given: the single slot is held and two executions wait in the queue
        FlowWithSource flow = queueFlow(1);
        harness.registerFlow(flow);
        ExecutorContext first = harness.start(flow);
        Execution second = Executions.created(flow);
        harness.handle(second);
        Execution third = Executions.created(flow);
        harness.handle(third);

        // When: the slot holder terminates
        Optional<Execution> firstPopped = release(terminated(flow, first.getExecution()));

        Assertions.assertThat(firstPopped).as("the oldest queued execution takes over the freed slot, already marked RUNNING").as("popped execution").map(Execution::getId)
            .hasValue(second.getId());
        Assertions.assertThat(firstPopped).as("popped execution state").map(e -> e.getState().getCurrent()).hasValue(State.Type.RUNNING);
        assertThat(harness).hasRunning(flow, 1);
        assertThat(harness).hasQueuedExactly(third);

        // When: the successor terminates in turn
        Optional<Execution> secondPopped = release(terminated(flow, firstPopped.get()));

        Assertions.assertThat(secondPopped).as("the last queued execution pops").as("popped execution").map(Execution::getId).hasValue(third.getId());
        assertThat(harness).hasRunning(flow, 1);
        assertThat(harness).hasNothingQueued();

        // When: the last one terminates with nobody waiting
        Optional<Execution> nonePopped = release(terminated(flow, secondPopped.get()));

        Assertions.assertThat(nonePopped).as("the queue is drained and every slot is free again").as("nothing popped").isEmpty();
        assertThat(harness).hasRunning(flow, 0);
    }

    @Test
    void shouldNotClaimSecondSlotWhenPoppedExecutionResumes() {
        // Given: the slot holder terminated and handed its slot to the queued execution
        FlowWithSource flow = queueFlow(1);
        harness.registerFlow(flow);
        ExecutorContext first = harness.start(flow);
        Execution second = Executions.created(flow);
        harness.handle(second);
        Execution popped = release(terminated(flow, first.getExecution())).orElseThrow();

        // When: DefaultExecutor emits the popped execution and its event comes back through the
        // executionQueue consumer — which persists the message first (the handler works on the
        // stored row) and maps a non-CREATED execution to an UPDATED event
        harness.executionStateStore().create(popped);
        ExecutorContext resumed = harness.executionEventMessageHandler()
            .handle(new ExecutionEvent(popped, ExecutionEventType.UPDATED))
            .orElseThrow();

        assertThat(resumed).as("the concurrency gate only guards CREATED (and restarted-failed) executions, so the resume neither claims a second slot nor parks the execution again")
            .executionInState(State.Type.RUNNING);
        assertThat(harness).hasRunning(flow, 1);
        assertThat(harness).hasNothingQueued();
    }

    @Test
    void shouldPopOnlyQueuedExecutionsOfTheSameFlow() {
        // Given: two flows in the SAME namespace, each with its single slot held and one
        // execution queued behind it
        Concurrency limitOne = Concurrency.builder().behavior(Concurrency.Behavior.QUEUE).limit(1).build();
        FlowWithSource flowA = Flows.of(Flows.builder(Flows.log()).id("drain-a").concurrency(limitOne).build());
        FlowWithSource flowB = Flows.of(Flows.builder(Flows.log()).id("drain-b").concurrency(limitOne).build());
        harness.registerFlow(flowA).registerFlow(flowB);
        ExecutorContext runnerA = harness.start(flowA);
        harness.start(flowB);
        Execution queuedA = Executions.created(flowA);
        harness.handle(queuedA);
        Execution queuedB = Executions.created(flowB);
        harness.handle(queuedB);

        // When: flow A's slot holder terminates
        Optional<Execution> popped = release(terminated(flowA, runnerA.getExecution()));

        Assertions.assertThat(popped).as("only flow A's queued execution pops — the freed slot never bleeds into flow B, whose execution stays parked behind its own untouched counter")
            .as("popped execution").map(Execution::getId).hasValue(queuedA.getId());
        assertThat(harness).hasQueuedExactly(queuedB);
        assertThat(harness).hasRunning(flowA, 1);
        assertThat(harness).hasRunning(flowB, 1);
    }

    // --- flows without a limit

    @Test
    void shouldLeaveConcurrencyMachineryUntouchedWhenFlowHasNoLimit() {
        // Given: a flow with no concurrency configuration
        FlowWithSource flow = Flows.of(Flows.log());
        harness.registerFlow(flow);

        // When: several executions start
        ExecutorContext first = harness.start(flow);
        ExecutorContext second = harness.start(flow);

        assertThat(first).as(
            "all run — an unlimited flow never touches the counter or the queued store, and its terminations release nothing (DefaultExecutor#toExecution skips the release processor entirely when the flow defines no concurrency)"
        ).executionInState(State.Type.RUNNING);
        assertThat(second).executionInState(State.Type.RUNNING);
        assertThat(harness).hasRunning(flow, 0);
        assertThat(harness).hasNothingQueued();
    }

    // --- re-entry

    @Test
    void shouldRequeueRestartedFailedExecutionWhenLimitStillReached() {
        // Given: the single slot is taken and a previously FAILED execution is RESTARTED
        FlowWithSource flow = queueFlow(1);
        harness.registerFlow(flow);
        harness.start(flow);
        Execution restarted = Executions.created(flow)
            .withState(State.Type.FAILED)
            .withState(State.Type.RESTARTED);
        // When: its event is handled again (the failedThenRestarted branch)
        ExecutorContext context = harness.handle(restarted);

        assertThat(context).as("the restart re-enters the concurrency gate and queues like a fresh execution").executionInState(State.Type.QUEUED).updatedFrom("handleConcurrencyLimit");
        assertThat(harness).hasRunning(flow, 1);
        assertThat(harness).hasQueuedExactly(restarted);
    }

    // --- terminal release edge cases (the kestra-ee#9200 / #16579 slot-leak family)

    @Test
    void shouldReleaseSlotWhenAdmittedExecutionIsFailedBySlaInTheAdmissionPass() {
        // Given: a single-slot flow whose execution-changed SLA always fails — the handler
        // admits the execution (stamping the claimed scopes) and then fails it in the very
        // same pass, while it is still CREATED
        FlowWithSource flow = Flows.of(
            Flows.builder(Flows.log())
                .concurrency(Concurrency.builder().behavior(Concurrency.Behavior.QUEUE).limit(1).build())
                .sla(
                    List.of(
                        ExecutionAssertionSLA.builder()
                            .id("always-fails")
                            .behavior(SLA.Behavior.FAIL)
                            ._assert("false")
                            .build()
                    )
                )
                .build()
        );
        harness.registerFlow(flow);

        // When
        ExecutorContext admitted = harness.start(flow);

        assertThat(admitted).as("failed straight out of CREATED, but carrying the claim stamp — its state history is indistinguishable from a gate rejection")
            .executionInState(State.Type.FAILED);
        assertThat(admitted).hasFirstState(State.Type.CREATED);
        assertThat(admitted).hasClaimStamp();
        assertThat(harness).hasRunning(flow, 1);

        // When: the terminal cycle releases
        Optional<Execution> popped = harness.concurrencySlotReleaseProcessor().release(admitted, true);

        Assertions.assertThat(popped).as("the stamp — not the CREATED→FAILED history heuristic — proves the slot was claimed, and it is returned instead of leaking forever")
            .as("nothing popped").isEmpty();
        assertThat(harness).hasRunning(flow, 0);

        // And the next execution is admitted instead of queueing behind a phantom holder
        // (it fails to the same SLA, but it RAN)
        ExecutorContext next = harness.start(flow);
        assertThat(next).hasClaimStamp();
    }

    @Test
    void shouldNotReleaseOrPopAgainWhenATerminalCycleIsRedelivered() {
        // Given: the slot holder terminated and its release already popped the first of two
        // queued executions
        FlowWithSource flow = queueFlow(1);
        harness.registerFlow(flow);
        ExecutorContext first = harness.start(flow);
        Execution second = Executions.created(flow);
        harness.handle(second);
        Execution third = Executions.created(flow);
        harness.handle(third);
        Execution terminal = first.getExecution().withState(State.Type.SUCCESS);
        Optional<Execution> popped = release(new ExecutorContext(first.getExecution(), flow).withExecution(terminal, "test"));
        Assertions.assertThat(popped).map(Execution::getId).hasValue(second.getId());

        // When: the terminal execution is redelivered — a cycle that already entered terminal
        // is not the one that terminated it, so DefaultExecutor passes terminatedByThisCycle=false
        Optional<Execution> redelivered = harness.concurrencySlotReleaseProcessor()
            .release(new ExecutorContext(terminal, flow).withExecution(terminal, "test"), false);

        Assertions.assertThat(redelivered).as("no double release — the successor's slot is still counted and the last queued execution is not over-admitted (#16579)").as("nothing popped")
            .isEmpty();
        assertThat(harness).hasRunning(flow, 1);
        assertThat(harness).hasQueuedExactly(third);
    }

    // --- fixtures

    private Optional<Execution> release(ExecutorContext terminated) {
        return harness.concurrencySlotReleaseProcessor().release(terminated, true);
    }

    private static ExecutorContext terminated(FlowWithSource flow, Execution running) {
        return new ExecutorContext(running, flow).withExecution(running.withState(State.Type.SUCCESS), "test");
    }

    private static FlowWithSource queueFlow(int limit) {
        return flowWithConcurrency(Concurrency.Behavior.QUEUE, limit);
    }

    private static FlowWithSource flowWithConcurrency(Concurrency.Behavior behavior, int limit) {
        return Flows.withConcurrency(
            Concurrency.builder().behavior(behavior).limit(limit).build(),
            Flows.log()
        );
    }
}
