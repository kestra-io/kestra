package io.kestra.executor;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Concurrency;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.ExecutionEvent;
import io.kestra.core.runners.ExecutionEventType;
import io.kestra.executor.testkit.Executions;
import io.kestra.executor.testkit.ExecutorTestHarness;
import io.kestra.executor.testkit.Flows;
import io.kestra.plugin.core.log.Log;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Decision matrix for {@link ConcurrencySlotReleaseProcessor} — what happens to the concurrency
 * counter and the queued store when a terminated execution's slot frees — plus the
 * transactional-outbox regression test for the bug class of kestra-io/kestra#17246: the pop runs
 * inside the concurrency-limit store's transaction, so it must hand back the popped execution
 * WITHOUT emitting a single queue message; emissions belong to the caller, after the transaction
 * commits. The guards (queued-then-killed, concurrency short-circuit, duplicate KILLED events)
 * were previously buried in {@code DefaultExecutor#toExecution} and untestable without a full
 * runner. The end-to-end twins live in {@code FlowConcurrencyCaseTest}; this runs in
 * milliseconds. No Micronaut, no database.
 */
class ConcurrencySlotReleaseProcessorTest {

    private final ExecutorTestHarness harness = ExecutorTestHarness.create();
    private final ConcurrencySlotReleaseProcessor processor = harness.concurrencySlotReleaseProcessor();

    // --- the transactional-outbox invariant (kestra-io/kestra#17246 bug class)

    @Test
    void shouldReturnPoppedExecutionMarkedRunningWithoutEmitting() {
        // Given: the single slot is held and a second execution is parked QUEUED
        FlowWithSource flow = queueFlow(1);
        harness.registerFlow(flow);
        ExecutorContext started = startExecution(flow);
        Execution second = Executions.created(flow);
        harness.executionStateStore().save(second);
        handleEvent(second);
        List<Integer> channelsAfterSetup = channelSizes();

        // When: the running execution terminates and its slot frees (inside the store's transaction)
        Optional<Execution> popped = processor.release(terminated(flow, started.getExecution(), State.Type.SUCCESS));

        // Then: the queued execution is popped, already marked RUNNING, and takes over the slot
        assertThat(popped).isPresent();
        assertThat(popped.get().getId()).isEqualTo(second.getId());
        assertThat(popped.get().getState().getCurrent()).isEqualTo(State.Type.RUNNING);
        assertThat(harness.concurrencyLimitStateStore().running(flow)).isEqualTo(1);
        assertThat(harness.executionQueuedStateStore().queued()).isEmpty();

        // and NOTHING was emitted during the release: the processor has no queue access by
        // design — the caller (DefaultExecutor#toExecution) emits the popped execution and fires
        // the flow triggers only after release() returns, i.e. after the transaction has committed
        assertThat(channelSizes()).isEqualTo(channelsAfterSetup);
    }

    // --- releasing without a successor

    @Test
    void shouldDecrementSlotWhenNoExecutionIsQueued() {
        // Given: the single slot is held, nothing queued
        FlowWithSource flow = queueFlow(1);
        harness.registerFlow(flow);
        ExecutorContext started = startExecution(flow);

        // When
        Optional<Execution> popped = processor.release(terminated(flow, started.getExecution(), State.Type.SUCCESS));

        // Then: the slot frees and nobody takes it over
        assertThat(popped).isEmpty();
        assertThat(harness.concurrencyLimitStateStore().running(flow)).isEqualTo(0);
    }

    @Test
    void shouldOnlyDecrementCounterForNonQueueBehavior() {
        // Given: a CANCEL-behavior flow never queues, so termination only frees the slot
        FlowWithSource flow = flowWithConcurrency(Concurrency.Behavior.CANCEL, 1);
        harness.registerFlow(flow);
        ExecutorContext started = startExecution(flow);

        // When
        Optional<Execution> popped = processor.release(terminated(flow, started.getExecution(), State.Type.SUCCESS));

        // Then
        assertThat(popped).isEmpty();
        assertThat(harness.concurrencyLimitStateStore().running(flow)).isEqualTo(0);
    }

    // --- guards: terminations that never held a slot must not release one

    @Test
    void shouldNotReleaseSlotWhenExecutionWasKilledFromQueue() {
        // Given: the slot is held and another execution is still parked QUEUED
        FlowWithSource flow = queueFlow(1);
        harness.registerFlow(flow);
        startExecution(flow);
        Execution stillQueued = Executions.created(flow);
        harness.executionStateStore().save(stillQueued);
        handleEvent(stillQueued);

        // and a third execution that was queued then killed WITHOUT ever running —
        // KILLING → KILLED, so the duplicate-kill guard alone would let it through
        Execution killedFromQueue = Executions.created(flow)
            .withState(State.Type.QUEUED)
            .withState(State.Type.KILLING);
        ExecutorContext killedContext = new ExecutorContext(killedFromQueue, flow)
            .withExecution(killedFromQueue.withState(State.Type.KILLED), "test");

        // When
        Optional<Execution> popped = processor.release(killedContext);

        // Then: it never claimed a slot, so nothing frees — the queued execution stays parked
        assertThat(popped).isEmpty();
        assertThat(harness.concurrencyLimitStateStore().running(flow)).isEqualTo(1);
        assertThat(harness.executionQueuedStateStore().queued()).hasSize(1);
    }

    @ParameterizedTest
    @EnumSource(value = State.Type.class, names = { "FAILED", "CANCELLED" })
    void shouldNotReleaseSlotWhenExecutionWasStoppedByConcurrencyShortCircuit(State.Type shortCircuitState) {
        // Given: the slot is held, and a second execution was terminated straight from CREATED
        // by the concurrency limit (FAIL/CANCEL behavior) — it never claimed a slot
        FlowWithSource flow = queueFlow(1);
        harness.registerFlow(flow);
        startExecution(flow);
        Execution shortCircuited = Executions.created(flow);
        ExecutorContext shortCircuitContext = new ExecutorContext(shortCircuited, flow)
            .withExecution(shortCircuited.withState(shortCircuitState), "handleConcurrencyLimit");

        // When
        Optional<Execution> popped = processor.release(shortCircuitContext);

        // Then: the holder's slot is untouched
        assertThat(popped).isEmpty();
        assertThat(harness.concurrencyLimitStateStore().running(flow)).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(value = State.Type.class, names = { "FAILED", "CANCELLED" })
    void shouldReleaseSlotWhenFormerlyQueuedExecutionTerminatesInError(State.Type errorState) {
        // Given: an execution that was queued, popped, and now holds the slot — its histories
        // are CREATED → QUEUED → RUNNING, one step away from the short-circuit shape
        // (CREATED → FAILED/CANCELLED) that must NOT release
        FlowWithSource flow = queueFlow(1);
        harness.registerFlow(flow);
        ExecutorContext started = startExecution(flow);
        Execution second = Executions.created(flow);
        harness.executionStateStore().save(second);
        handleEvent(second);
        Execution popped = processor.release(terminated(flow, started.getExecution(), State.Type.SUCCESS)).orElseThrow();

        // When: it terminates in error during its actual run
        Optional<Execution> next = processor.release(
            new ExecutorContext(popped, flow).withExecution(popped.withState(errorState), "test")
        );

        // Then: a genuine run failure releases the slot like any termination — the short-circuit
        // guard only holds when the error state follows CREATED directly
        assertThat(next).isEmpty();
        assertThat(harness.concurrencyLimitStateStore().running(flow)).isEqualTo(0);
    }

    @Test
    void shouldReleaseSlotOnlyForTheFirstKilledEvent() {
        // Given: the slot holder is killed while a second execution waits in the queue
        FlowWithSource flow = queueFlow(1);
        harness.registerFlow(flow);
        ExecutorContext started = startExecution(flow);
        Execution second = Executions.created(flow);
        harness.executionStateStore().save(second);
        handleEvent(second);

        Execution killing = started.getExecution().withState(State.Type.KILLING);

        // When: the KILLING → KILLED transition arrives (the first KILLED event)
        ExecutorContext firstKilledEvent = new ExecutorContext(killing, flow)
            .withExecution(killing.withState(State.Type.KILLED), "test");
        Optional<Execution> popped = processor.release(firstKilledEvent);

        // Then: the slot hands over to the queued execution
        assertThat(popped).isPresent();
        assertThat(popped.get().getId()).isEqualTo(second.getId());
        assertThat(harness.concurrencyLimitStateStore().running(flow)).isEqualTo(1);

        // When: the same KILLED execution is observed again (one event arrives per running
        // worker task) — the context now enters already KILLED instead of KILLING
        Execution killed = killing.withState(State.Type.KILLED);
        Optional<Execution> duplicate = processor.release(new ExecutorContext(killed, flow));

        // Then: no double release — the successor keeps its slot
        assertThat(duplicate).isEmpty();
        assertThat(harness.concurrencyLimitStateStore().running(flow)).isEqualTo(1);
    }

    // --- fixtures

    private ExecutorContext terminated(FlowWithSource flow, Execution running, State.Type terminal) {
        return new ExecutorContext(running, flow).withExecution(running.withState(terminal), "test");
    }

    private List<Integer> channelSizes() {
        return List.of(
            harness.executionQueue().emitted().size(),
            harness.executionCommandQueue().emitted().size(),
            harness.workerJobEventQueue().emitted().size(),
            harness.subflowExecutionResultQueue().emitted().size(),
            harness.followExecutionEventQueue().emitted().size(),
            harness.kills().size(),
            harness.loopEvents().size()
        );
    }

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
