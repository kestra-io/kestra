package io.kestra.executor.statemachine;

import java.util.List;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKilledExecution;
import io.kestra.core.models.flows.Concurrency;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.State;
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
 * counter is keyed, and how killing a queued execution releases it — no Micronaut, no database.
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
