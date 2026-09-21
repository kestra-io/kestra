package io.kestra.executor;

import java.util.List;
import java.util.Optional;

import org.jooq.exception.DataAccessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Concurrency;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.ExecutionQueuedStateStore;
import io.kestra.core.runners.FlowMetaStoreInterface;
import io.kestra.core.runners.ScopedConcurrencyLimit;
import io.kestra.core.services.ConcurrencyLimitResolver;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.core.log.Log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * The release decision of {@link ConcurrencySlotReleaseProcessor}: a slot is given back only when
 * the execution actually claimed one and this cycle is the one that terminated it.
 */
class ConcurrencySlotReleaseProcessorTest {

    private ConcurrencyLimitStateStore stateStore;
    private ConcurrencySlotReleaseProcessor processor;
    private FlowWithSource flow;

    @BeforeEach
    void setUp() {
        Flow definition = Flow.builder()
            .tenantId("main")
            .namespace("io.kestra.tests")
            .id(IdUtils.create())
            .revision(1)
            .concurrency(Concurrency.builder().limit(1).behavior(Concurrency.Behavior.QUEUE).build())
            .tasks(List.of(Log.builder().id("log").type(Log.class.getName()).message("test").build()))
            .build();
        this.flow = FlowWithSource.of(definition, "");

        ConcurrencyLimitResolver resolver = Mockito.mock(ConcurrencyLimitResolver.class);
        Mockito.when(resolver.resolveLimits(Mockito.any())).thenReturn(List.of(ScopedConcurrencyLimit.ofFlow(definition)));

        this.stateStore = Mockito.mock(ConcurrencyLimitStateStore.class);
        this.processor = new ConcurrencySlotReleaseProcessor(
            stateStore,
            resolver,
            Mockito.mock(ExecutionQueuedStateStore.class),
            Mockito.mock(FlowMetaStoreInterface.class),
            Mockito.mock(MetricRegistry.class, Mockito.RETURNS_DEEP_STUBS)
        );
    }

    @Test
    void shouldReleaseWhenAdmittedExecutionFailedStraightOutOfCreated() {
        // Given: an execution the gate admitted, then terminated while still CREATED — the shape a
        // gate-rejected execution also has, which is why the state history alone cannot decide
        // (kestra-ee#9200)
        Execution execution = stamped(created().withState(State.Type.FAILED));

        // When
        processor.release(cycle(created(), execution), true);

        // Then
        verifyReleased();
    }

    @Test
    void shouldReleaseWhenAdmittedExecutionCancelledStraightOutOfCreated() {
        // Given
        Execution execution = stamped(created().withState(State.Type.CANCELLED));

        // When
        processor.release(cycle(created(), execution), true);

        // Then
        verifyReleased();
    }

    @Test
    void shouldNotReleaseWhenTheExecutionWasAlreadyTerminatedByAnEarlierCycle() {
        // Given: a duplicate terminal cycle — a second SubflowExecutionResult on an already FAILED
        // parent used to release a second slot and over-admit (GitHub #16579)
        Execution execution = stamped(created().withState(State.Type.RUNNING).withState(State.Type.FAILED));

        // When
        processor.release(cycle(execution, execution), false);

        // Then
        verifyNotReleased();
    }

    @Test
    void shouldReleaseWhenTheKillCycleTerminatesTheExecution() {
        // Given
        Execution killed = stamped(
            created().withState(State.Type.RUNNING).withState(State.Type.KILLING).withState(State.Type.KILLED)
        );

        // When
        processor.release(cycle(created().withState(State.Type.RUNNING).withState(State.Type.KILLING), killed), true);

        // Then
        verifyReleased();
    }

    @Test
    void shouldNotReleaseWhenAFurtherKillMessageRepeatsATerminatedExecution() {
        // Given: a killed execution is re-delivered once per running worker task
        Execution killed = stamped(
            created().withState(State.Type.RUNNING).withState(State.Type.KILLING).withState(State.Type.KILLED)
        );

        // When
        processor.release(cycle(killed, killed), false);

        // Then
        verifyNotReleased();
    }

    @Test
    void shouldNotReleaseWhenAnUnstampedExecutionWasQueuedThenKilled() {
        // Given: no claim stamp — admitted by a version that did not stamp yet, so the state
        // history is the only signal. A queued execution never held a slot.
        Execution execution = created().withState(State.Type.QUEUED).withState(State.Type.KILLED);

        // When
        processor.release(cycle(created().withState(State.Type.QUEUED), execution), true);

        // Then
        verifyNotReleased();
    }

    @Test
    void shouldNotReleaseWhenAnUnstampedExecutionWasRejectedByTheGate() {
        // Given
        Execution execution = created().withState(State.Type.CANCELLED);

        // When
        processor.release(cycle(created(), execution), true);

        // Then
        verifyNotReleased();
    }

    @Test
    void shouldReleaseWhenAnUnstampedExecutionRanToCompletion() {
        // Given
        Execution execution = created().withState(State.Type.RUNNING).withState(State.Type.SUCCESS);

        // When
        processor.release(cycle(created().withState(State.Type.RUNNING), execution), true);

        // Then
        verifyReleased();
    }

    @Test
    void shouldReturnEmptyWithoutTouchingTheStoreWhenNoLimitAppliesToTheFlow() {
        // Given
        ConcurrencyLimitResolver noLimit = Mockito.mock(ConcurrencyLimitResolver.class);
        Mockito.when(noLimit.resolveLimits(Mockito.any())).thenReturn(List.of());
        ConcurrencySlotReleaseProcessor unlimited = new ConcurrencySlotReleaseProcessor(
            stateStore,
            noLimit,
            Mockito.mock(ExecutionQueuedStateStore.class),
            Mockito.mock(FlowMetaStoreInterface.class),
            Mockito.mock(MetricRegistry.class, Mockito.RETURNS_DEEP_STUBS)
        );
        // an execution without the claim stamp: nothing tells the processor which scopes to give
        // back, and the flow declares none either
        Execution execution = created().withState(State.Type.RUNNING).withState(State.Type.SUCCESS);

        // When
        Optional<Execution> popped = unlimited.release(cycle(created().withState(State.Type.RUNNING), execution), true);

        // Then
        assertThat(popped).isEmpty();
        verifyNotReleased();
    }

    @Test
    void shouldReleaseTheStampedScopeWhenTheLimitWasRemovedWhileTheExecutionRan() {
        // Given: the flow no longer declares a limit, but the execution was admitted under one —
        // the counter must not keep the slot it claimed
        ConcurrencyLimitResolver noLimit = Mockito.mock(ConcurrencyLimitResolver.class);
        Mockito.when(noLimit.resolveLimits(Mockito.any())).thenReturn(List.of());
        ConcurrencySlotReleaseProcessor unlimited = new ConcurrencySlotReleaseProcessor(
            stateStore,
            noLimit,
            Mockito.mock(ExecutionQueuedStateStore.class),
            Mockito.mock(FlowMetaStoreInterface.class),
            Mockito.mock(MetricRegistry.class, Mockito.RETURNS_DEEP_STUBS)
        );
        Execution execution = stamped(created().withState(State.Type.RUNNING).withState(State.Type.SUCCESS));

        // When
        unlimited.release(cycle(created().withState(State.Type.RUNNING), execution), true);

        // Then
        verifyReleased();
    }

    @Test
    void shouldReturnEmptyRatherThanPropagateWhenTheReleaseFails() {
        // Given: the release runs after the terminated execution row has been committed, so letting
        // a database failure escape would reach the queue subscriber's fatal-error handling and
        // shut the instance down
        Mockito
            .when(stateStore.releaseThenPop(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
            .thenThrow(new DataAccessException("deadlock"));
        Execution execution = stamped(created().withState(State.Type.RUNNING).withState(State.Type.SUCCESS));
        ExecutorContext executor = cycle(created().withState(State.Type.RUNNING), execution);

        // When / Then
        assertThatCode(() -> assertThat(processor.release(executor, true)).isEmpty()).doesNotThrowAnyException();
    }

    // --- fixtures

    private Execution created() {
        return Execution.newExecution(flow, List.of());
    }

    /** Stamps the claim the admission gate persists on an execution it admitted. */
    private Execution stamped(Execution execution) {
        return execution.withMetadata(
            execution.getMetadata().withConcurrencyScopes(List.of(ScopedConcurrencyLimit.ofFlow(flow).uid()))
        );
    }

    /** A cycle that entered with {@code entry} and left with {@code current}. */
    private ExecutorContext cycle(Execution entry, Execution current) {
        return new ExecutorContext(entry, flow).withExecution(current, "test");
    }

    private void verifyReleased() {
        Mockito.verify(stateStore).releaseThenPop(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    private void verifyNotReleased() {
        Mockito.verify(stateStore, Mockito.never()).releaseThenPop(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }
}
