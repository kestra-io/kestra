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
import io.kestra.plugin.core.log.Log;

import reactor.core.publisher.Flux;

import static io.kestra.executor.testkit.ExecutorContextAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

/**
 * Layer-1 sagas of the concurrency-limit lifecycle through the real
 * {@code ExecutionEventMessageHandler}: how an execution claims a slot in the running counter,
 * how the QUEUE/CANCEL/FAIL behaviors interact with the counter and the queued store, how the
 * counter is keyed, how killing a queued execution releases it, and how terminations drain the
 * queue through the {@code ConcurrencySlotReleaseProcessor} — no Micronaut, no database.
 * The full-runner twins live in {@code FlowConcurrencyCaseTest}/{@code AbstractRunnerConcurrencyTest}
 * (seconds per scenario over a real backend); these run in milliseconds.
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
        ExecutorContext started = startExecution(flow);

        // Then: the execution runs and holds one slot; nothing is queued
        Assertions.assertThat(started.getExecution().getState().getCurrent()).isEqualTo(State.Type.RUNNING);
        Assertions.assertThat(harness.concurrencyLimitStateStore().running(flow)).isEqualTo(1);
        Assertions.assertThat(harness.executionQueuedStateStore().queued()).isEmpty();
    }

    @Test
    void shouldQueueExecutionWithoutClaimingSlotWhenLimitReached() {
        // Given: the single slot is taken
        FlowWithSource flow = queueFlow(1);
        harness.registerFlow(flow);
        startExecution(flow);

        // When: a second execution arrives
        Execution second = Executions.created(flow);
        harness.executionStateStore().save(second);
        ExecutorContext context = handleEvent(second);

        // Then: it is parked QUEUED by the concurrency short-circuit — no slot claimed, no task run
        assertThat(context).executionInState(State.Type.QUEUED).updatedFrom("handleConcurrencyLimit");
        Assertions.assertThat(context.getExecution().getTaskRunList()).isNullOrEmpty();
        Assertions.assertThat(harness.concurrencyLimitStateStore().running(flow)).isEqualTo(1);
        Assertions.assertThat(harness.executionQueuedStateStore().queued())
            .singleElement()
            .satisfies(queued -> Assertions.assertThat(queued.getExecution().getId()).isEqualTo(second.getId()));
    }

    @Test
    void shouldCancelExecutionWithoutClaimingSlotWhenLimitReached() {
        // Given
        FlowWithSource flow = flowWithConcurrency(Concurrency.Behavior.CANCEL, 1);
        harness.registerFlow(flow);
        startExecution(flow);

        // When
        Execution second = Executions.created(flow);
        harness.executionStateStore().save(second);
        ExecutorContext context = handleEvent(second);

        // Then: CANCELLED terminal, counter untouched (it never ran), nothing queued
        assertThat(context).executionInState(State.Type.CANCELLED).updatedFrom("handleConcurrencyLimit");
        Assertions.assertThat(harness.concurrencyLimitStateStore().running(flow)).isEqualTo(1);
        Assertions.assertThat(harness.executionQueuedStateStore().queued()).isEmpty();
    }

    @Test
    void shouldFailExecutionWithoutClaimingSlotWhenLimitReached() {
        // Given
        FlowWithSource flow = flowWithConcurrency(Concurrency.Behavior.FAIL, 1);
        harness.registerFlow(flow);
        startExecution(flow);

        // When
        Execution second = Executions.created(flow);
        harness.executionStateStore().save(second);
        ExecutorContext context = handleEvent(second);

        // Then
        assertThat(context).executionInState(State.Type.FAILED).updatedFrom("handleConcurrencyLimit");
        Assertions.assertThat(harness.concurrencyLimitStateStore().running(flow)).isEqualTo(1);
        Assertions.assertThat(harness.executionQueuedStateStore().queued()).isEmpty();
    }

    @Test
    void shouldFillEverySlotBeforeQueueing() {
        // Given: a limit of 2
        FlowWithSource flow = queueFlow(2);
        harness.registerFlow(flow);

        // When: two executions start
        ExecutorContext first = startExecution(flow);
        ExecutorContext second = startExecution(flow);

        // Then: both run — the limit does not trip one short of the boundary
        Assertions.assertThat(first.getExecution().getState().getCurrent()).isEqualTo(State.Type.RUNNING);
        Assertions.assertThat(second.getExecution().getState().getCurrent()).isEqualTo(State.Type.RUNNING);
        Assertions.assertThat(harness.concurrencyLimitStateStore().running(flow)).isEqualTo(2);

        // When: a third arrives
        Execution third = Executions.created(flow);
        harness.executionStateStore().save(third);
        ExecutorContext context = handleEvent(third);

        // Then: exactly at the limit it queues
        assertThat(context).executionInState(State.Type.QUEUED).updatedFrom("handleConcurrencyLimit");
        Assertions.assertThat(harness.concurrencyLimitStateStore().running(flow)).isEqualTo(2);
    }

    @Test
    void shouldKeepQueuedExecutionsInArrivalOrder() {
        // Given: the single slot is taken
        FlowWithSource flow = queueFlow(1);
        harness.registerFlow(flow);
        startExecution(flow);

        // When: two more executions arrive
        Execution second = Executions.created(flow);
        harness.executionStateStore().save(second);
        handleEvent(second);
        Execution third = Executions.created(flow);
        harness.executionStateStore().save(third);
        handleEvent(third);

        // Then: both park in arrival order — the order decrementAndPop replays them in —
        // and still only one slot is held
        Assertions.assertThat(harness.executionQueuedStateStore().queued())
            .extracting(queued -> queued.getExecution().getId())
            .containsExactly(second.getId(), third.getId());
        Assertions.assertThat(harness.concurrencyLimitStateStore().running(flow)).isEqualTo(1);
    }

    // --- how the counter is keyed

    @Test
    void shouldKeepCountersIndependentAcrossNamespaces() {
        // Given: two flows with the same id in different namespaces, each limited to 1
        Concurrency limitOne = Concurrency.builder().behavior(Concurrency.Behavior.QUEUE).limit(1).build();
        FlowWithSource flowA = Flows.of(Flows.builder(logTask()).id("same-id").concurrency(limitOne).build());
        FlowWithSource flowB = Flows.of(
            Flows.builder(logTask()).id("same-id").namespace(Flows.NAMESPACE + ".other").concurrency(limitOne).build()
        );
        harness.registerFlow(flowA).registerFlow(flowB);

        // When: both namespaces start an execution
        startExecution(flowA);
        ExecutorContext startedB = startExecution(flowB);

        // Then: B runs — A's full slot does not bleed into B's counter
        Assertions.assertThat(startedB.getExecution().getState().getCurrent()).isEqualTo(State.Type.RUNNING);
        Assertions.assertThat(harness.concurrencyLimitStateStore().running(flowA)).isEqualTo(1);
        Assertions.assertThat(harness.concurrencyLimitStateStore().running(flowB)).isEqualTo(1);
        Assertions.assertThat(harness.executionQueuedStateStore().queued()).isEmpty();
    }

    @Test
    void shouldKeepCountersIndependentAcrossTenants() {
        // Given: two flows with the same id AND namespace in different tenants, each limited to 1
        Concurrency limitOne = Concurrency.builder().behavior(Concurrency.Behavior.QUEUE).limit(1).build();
        FlowWithSource flowA = Flows.of(Flows.builder(logTask()).id("tenant-keyed").concurrency(limitOne).build());
        FlowWithSource flowB = Flows.of(
            Flows.builder(logTask()).id("tenant-keyed").tenantId("other-tenant").concurrency(limitOne).build()
        );
        harness.registerFlow(flowA).registerFlow(flowB);

        // When: both tenants start an execution
        startExecution(flowA);
        ExecutorContext startedB = startExecution(flowB);

        // Then: B runs — a tenant's full slot never bleeds into another tenant's counter
        Assertions.assertThat(startedB.getExecution().getState().getCurrent()).isEqualTo(State.Type.RUNNING);
        Assertions.assertThat(harness.concurrencyLimitStateStore().running(flowA)).isEqualTo(1);
        Assertions.assertThat(harness.concurrencyLimitStateStore().running(flowB)).isEqualTo(1);
        Assertions.assertThat(harness.executionQueuedStateStore().queued()).isEmpty();
    }

    @Test
    void shouldShareCounterAcrossFlowRevisions() {
        // Given: revision 1 holds the single slot
        Concurrency limitOne = Concurrency.builder().behavior(Concurrency.Behavior.QUEUE).limit(1).build();
        FlowWithSource revision1 = Flows.of(Flows.builder(logTask()).id("revisioned").concurrency(limitOne).build());
        harness.registerFlow(revision1);
        startExecution(revision1);

        // When: an execution of revision 2 of the same flow arrives
        FlowWithSource revision2 = Flows.of(
            Flows.builder(logTask()).id("revisioned").revision(2).concurrency(limitOne).build()
        );
        harness.registerFlow(revision2);
        Execution second = Executions.created(revision2);
        harness.executionStateStore().save(second);
        ExecutorContext context = handleEvent(second);

        // Then: the counter is keyed without the revision — revision 2 queues behind revision 1
        assertThat(context).executionInState(State.Type.QUEUED);
        Assertions.assertThat(harness.concurrencyLimitStateStore().running(revision2)).isEqualTo(1);
    }

    @Test
    void shouldApplyLoweredLimitFromLatestFlowDefinition() {
        // Given: two executions admitted under revision 1's limit of 3
        Concurrency limitThree = Concurrency.builder().behavior(Concurrency.Behavior.QUEUE).limit(3).build();
        FlowWithSource revision1 = Flows.of(Flows.builder(logTask()).id("lowered-limit").concurrency(limitThree).build());
        harness.registerFlow(revision1);
        startExecution(revision1);
        startExecution(revision1);
        Assertions.assertThat(harness.concurrencyLimitStateStore().running(revision1)).isEqualTo(2);

        // When: revision 2 lowers the limit to 1 and a new execution arrives
        Concurrency limitOne = Concurrency.builder().behavior(Concurrency.Behavior.QUEUE).limit(1).build();
        FlowWithSource revision2 = Flows.of(
            Flows.builder(logTask()).id("lowered-limit").revision(2).concurrency(limitOne).build()
        );
        harness.registerFlow(revision2);
        Execution third = Executions.created(revision2);
        harness.executionStateStore().save(third);
        ExecutorContext context = handleEvent(third);

        // Then: the latest definition's limit governs — 2 running >= 1 queues the arrival even
        // though both runs were admitted under the laxer revision, and no slot is reclaimed
        assertThat(context).executionInState(State.Type.QUEUED).updatedFrom("handleConcurrencyLimit");
        Assertions.assertThat(harness.concurrencyLimitStateStore().running(revision2)).isEqualTo(2);
        Assertions.assertThat(harness.executionQueuedStateStore().queued())
            .singleElement()
            .satisfies(queued -> Assertions.assertThat(queued.getExecution().getId()).isEqualTo(third.getId()));
    }

    // --- leaving the queue

    @Test
    void shouldRemoveKilledExecutionFromQueueWithoutReleasingASlot() {
        // Given: a queued execution behind a full slot
        FlowWithSource flow = queueFlow(1);
        harness.registerFlow(flow);
        startExecution(flow);
        Execution second = Executions.created(flow);
        harness.executionStateStore().save(second);
        handleEvent(second);
        Assertions.assertThat(harness.executionQueuedStateStore().queued()).hasSize(1);

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

        // Then: it leaves the queued store so a freed slot can never restart it, and the running
        // counter is untouched — a queued execution never claimed a slot, so its kill releases none
        Assertions.assertThat(killed).isPresent();
        Assertions.assertThat(killed.get().getExecution().getState().getCurrent()).isEqualTo(State.Type.KILLED);
        Assertions.assertThat(harness.executionQueuedStateStore().queued()).isEmpty();
        Assertions.assertThat(harness.concurrencyLimitStateStore().running(flow)).isEqualTo(1);
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
        ExecutorContext first = startExecution(flow);
        Execution second = Executions.created(flow);
        harness.executionStateStore().save(second);
        handleEvent(second);
        Execution third = Executions.created(flow);
        harness.executionStateStore().save(third);
        handleEvent(third);

        // When: the slot holder terminates
        Optional<Execution> firstPopped = release(terminated(flow, first.getExecution()));

        // Then: the oldest queued execution takes over the freed slot, already marked RUNNING
        Assertions.assertThat(firstPopped).isPresent();
        Assertions.assertThat(firstPopped.get().getId()).isEqualTo(second.getId());
        Assertions.assertThat(firstPopped.get().getState().getCurrent()).isEqualTo(State.Type.RUNNING);
        Assertions.assertThat(harness.concurrencyLimitStateStore().running(flow)).isEqualTo(1);
        Assertions.assertThat(harness.executionQueuedStateStore().queued())
            .extracting(queued -> queued.getExecution().getId())
            .containsExactly(third.getId());

        // When: the successor terminates in turn
        Optional<Execution> secondPopped = release(terminated(flow, firstPopped.get()));

        // Then: the last queued execution pops
        Assertions.assertThat(secondPopped).isPresent();
        Assertions.assertThat(secondPopped.get().getId()).isEqualTo(third.getId());
        Assertions.assertThat(harness.concurrencyLimitStateStore().running(flow)).isEqualTo(1);
        Assertions.assertThat(harness.executionQueuedStateStore().queued()).isEmpty();

        // When: the last one terminates with nobody waiting
        Optional<Execution> nonePopped = release(terminated(flow, secondPopped.get()));

        // Then: the queue is drained and every slot is free again
        Assertions.assertThat(nonePopped).isEmpty();
        Assertions.assertThat(harness.concurrencyLimitStateStore().running(flow)).isEqualTo(0);
    }

    @Test
    void shouldNotClaimSecondSlotWhenPoppedExecutionResumes() {
        // Given: the slot holder terminated and handed its slot to the queued execution
        FlowWithSource flow = queueFlow(1);
        harness.registerFlow(flow);
        ExecutorContext first = startExecution(flow);
        Execution second = Executions.created(flow);
        harness.executionStateStore().save(second);
        handleEvent(second);
        Execution popped = release(terminated(flow, first.getExecution())).orElseThrow();

        // When: DefaultExecutor emits the popped execution and its event comes back through the
        // executionQueue consumer — which persists the message first (the handler works on the
        // stored row) and maps a non-CREATED execution to an UPDATED event
        harness.executionStateStore().create(popped);
        ExecutorContext resumed = harness.executionEventMessageHandler()
            .handle(new ExecutionEvent(popped, ExecutionEventType.UPDATED))
            .orElseThrow();

        // Then: the concurrency gate only guards CREATED (and restarted-failed) executions, so
        // the resume neither claims a second slot nor parks the execution again
        Assertions.assertThat(resumed.getExecution().getState().getCurrent()).isEqualTo(State.Type.RUNNING);
        Assertions.assertThat(harness.concurrencyLimitStateStore().running(flow)).isEqualTo(1);
        Assertions.assertThat(harness.executionQueuedStateStore().queued()).isEmpty();
    }

    @Test
    void shouldPopOnlyQueuedExecutionsOfTheSameFlow() {
        // Given: two flows in the SAME namespace, each with its single slot held and one
        // execution queued behind it
        Concurrency limitOne = Concurrency.builder().behavior(Concurrency.Behavior.QUEUE).limit(1).build();
        FlowWithSource flowA = Flows.of(Flows.builder(logTask()).id("drain-a").concurrency(limitOne).build());
        FlowWithSource flowB = Flows.of(Flows.builder(logTask()).id("drain-b").concurrency(limitOne).build());
        harness.registerFlow(flowA).registerFlow(flowB);
        ExecutorContext runnerA = startExecution(flowA);
        startExecution(flowB);
        Execution queuedA = Executions.created(flowA);
        harness.executionStateStore().save(queuedA);
        handleEvent(queuedA);
        Execution queuedB = Executions.created(flowB);
        harness.executionStateStore().save(queuedB);
        handleEvent(queuedB);

        // When: flow A's slot holder terminates
        Optional<Execution> popped = release(terminated(flowA, runnerA.getExecution()));

        // Then: only flow A's queued execution pops — the freed slot never bleeds into flow B,
        // whose execution stays parked behind its own untouched counter
        Assertions.assertThat(popped).isPresent();
        Assertions.assertThat(popped.get().getId()).isEqualTo(queuedA.getId());
        Assertions.assertThat(harness.executionQueuedStateStore().queued())
            .extracting(queued -> queued.getExecution().getId())
            .containsExactly(queuedB.getId());
        Assertions.assertThat(harness.concurrencyLimitStateStore().running(flowA)).isEqualTo(1);
        Assertions.assertThat(harness.concurrencyLimitStateStore().running(flowB)).isEqualTo(1);
    }

    // --- flows without a limit

    @Test
    void shouldLeaveConcurrencyMachineryUntouchedWhenFlowHasNoLimit() {
        // Given: a flow with no concurrency configuration
        FlowWithSource flow = Flows.of(logTask());
        harness.registerFlow(flow);

        // When: several executions start
        ExecutorContext first = startExecution(flow);
        ExecutorContext second = startExecution(flow);

        // Then: all run — an unlimited flow never touches the counter or the queued store, and
        // its terminations release nothing (DefaultExecutor#toExecution skips the release
        // processor entirely when the flow defines no concurrency)
        Assertions.assertThat(first.getExecution().getState().getCurrent()).isEqualTo(State.Type.RUNNING);
        Assertions.assertThat(second.getExecution().getState().getCurrent()).isEqualTo(State.Type.RUNNING);
        Assertions.assertThat(harness.concurrencyLimitStateStore().running(flow)).isEqualTo(0);
        Assertions.assertThat(harness.executionQueuedStateStore().queued()).isEmpty();
    }

    // --- re-entry

    @Test
    void shouldRequeueRestartedFailedExecutionWhenLimitStillReached() {
        // Given: the single slot is taken and a previously FAILED execution is RESTARTED
        FlowWithSource flow = queueFlow(1);
        harness.registerFlow(flow);
        startExecution(flow);
        Execution restarted = Executions.created(flow)
            .withState(State.Type.FAILED)
            .withState(State.Type.RESTARTED);
        harness.executionStateStore().save(restarted);

        // When: its event is handled again (the failedThenRestarted branch)
        ExecutorContext context = handleEvent(restarted);

        // Then: the restart re-enters the concurrency gate and queues like a fresh execution
        assertThat(context).executionInState(State.Type.QUEUED).updatedFrom("handleConcurrencyLimit");
        Assertions.assertThat(harness.concurrencyLimitStateStore().running(flow)).isEqualTo(1);
        Assertions.assertThat(harness.executionQueuedStateStore().queued())
            .singleElement()
            .satisfies(queued -> Assertions.assertThat(queued.getExecution().getId()).isEqualTo(restarted.getId()));
    }

    // --- terminal release edge cases (the kestra-ee#9200 / #16579 slot-leak family)

    @Test
    void shouldReleaseSlotWhenAdmittedExecutionIsFailedBySlaInTheAdmissionPass() {
        // Given: a single-slot flow whose execution-changed SLA always fails — the handler
        // admits the execution (stamping the claimed scopes) and then fails it in the very
        // same pass, while it is still CREATED
        FlowWithSource flow = Flows.of(
            Flows.builder(logTask())
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
        ExecutorContext admitted = startExecution(flow);

        // Then: failed straight out of CREATED, but carrying the claim stamp — its state
        // history is indistinguishable from a gate rejection
        Assertions.assertThat(admitted.getExecution().getState().getCurrent()).isEqualTo(State.Type.FAILED);
        Assertions.assertThat(admitted.getExecution().getState().getHistories().getFirst().getState()).isEqualTo(State.Type.CREATED);
        Assertions.assertThat(admitted.getExecution().getMetadata().getConcurrencyScopes()).isNotEmpty();
        Assertions.assertThat(harness.concurrencyLimitStateStore().running(flow)).isEqualTo(1);

        // When: the terminal cycle releases
        Optional<Execution> popped = harness.concurrencySlotReleaseProcessor().release(admitted, true);

        // Then: the stamp — not the CREATED→FAILED history heuristic — proves the slot was
        // claimed, and it is returned instead of leaking forever
        Assertions.assertThat(popped).isEmpty();
        Assertions.assertThat(harness.concurrencyLimitStateStore().running(flow)).isEqualTo(0);

        // And the next execution is admitted instead of queueing behind a phantom holder
        // (it fails to the same SLA, but it RAN)
        ExecutorContext next = startExecution(flow);
        Assertions.assertThat(next.getExecution().getMetadata().getConcurrencyScopes()).isNotEmpty();
    }

    @Test
    void shouldNotReleaseOrPopAgainWhenATerminalCycleIsRedelivered() {
        // Given: the slot holder terminated and its release already popped the first of two
        // queued executions
        FlowWithSource flow = queueFlow(1);
        harness.registerFlow(flow);
        ExecutorContext first = startExecution(flow);
        Execution second = Executions.created(flow);
        harness.executionStateStore().save(second);
        handleEvent(second);
        Execution third = Executions.created(flow);
        harness.executionStateStore().save(third);
        handleEvent(third);
        Execution terminal = first.getExecution().withState(State.Type.SUCCESS);
        Optional<Execution> popped = release(new ExecutorContext(first.getExecution(), flow).withExecution(terminal, "test"));
        Assertions.assertThat(popped).map(Execution::getId).hasValue(second.getId());

        // When: the terminal execution is redelivered — a cycle that already entered terminal
        // is not the one that terminated it, so DefaultExecutor passes terminatedByThisCycle=false
        Optional<Execution> redelivered = harness.concurrencySlotReleaseProcessor()
            .release(new ExecutorContext(terminal, flow).withExecution(terminal, "test"), false);

        // Then: no double release — the successor's slot is still counted and the last queued
        // execution is not over-admitted (#16579)
        Assertions.assertThat(redelivered).isEmpty();
        Assertions.assertThat(harness.concurrencyLimitStateStore().running(flow)).isEqualTo(1);
        Assertions.assertThat(harness.executionQueuedStateStore().queued())
            .extracting(queued -> queued.getExecution().getId())
            .containsExactly(third.getId());
    }

    // --- fixtures

    /**
     * Create the execution, persist it, and run its CREATED event through the real handler —
     * the exact path a webserver/scheduler submission takes.
     */
    private ExecutorContext startExecution(FlowWithSource flow) {
        Execution execution = Executions.created(flow);
        harness.executionStateStore().save(execution);
        return handleEvent(execution);
    }

    private ExecutorContext handleEvent(Execution execution) {
        return harness.executionEventMessageHandler()
            .handle(new ExecutionEvent(execution, ExecutionEventType.CREATED))
            .orElseThrow();
    }

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
            logTask()
        );
    }

    private static Log logTask() {
        return Log.builder().id("log").type(Log.class.getName()).message("hello").build();
    }
}
