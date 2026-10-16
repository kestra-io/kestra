package io.kestra.executor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.kestra.core.models.Label;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.flows.sla.SLA;
import io.kestra.core.models.flows.sla.SLAMonitor;
import io.kestra.core.models.flows.sla.types.MaxDurationSLA;
import io.kestra.executor.testkit.Executions;
import io.kestra.executor.testkit.ExecutorTestHarness;
import io.kestra.executor.testkit.Flows;

import static io.kestra.executor.testkit.ExecutorContextAssert.assertThat;
import static io.kestra.executor.testkit.HarnessAssert.assertThat;

/**
 * Decision matrix for {@link SLAMonitorProcessor} — what happens when an execution-monitoring
 */
class SLAMonitorProcessorTest {

    private static final Instant NOW = Instant.parse("2026-07-07T10:00:00Z");

    private final ExecutorTestHarness harness = ExecutorTestHarness.create();
    private final SLAMonitorProcessor processor = harness.slaMonitorProcessor();

    // --- the transactional-outbox invariant

    @Test
    void shouldReturnViolatedContextWithoutEmittingWhenSlaOnlyAddsLabels() {
        // Given: a running execution whose labels-only MAX_DURATION SLA (behavior NONE) is
        // already violated, and its monitor deadline has passed
        FlowWithSource flow = slaFlow(maxDuration(SLA.Behavior.NONE, Duration.ZERO, List.of(new Label("sla", "violated"))));
        harness.registerFlow(flow);
        Execution running = runningExecution(flow);
        harness.slaMonitorStateStore().save(monitor(running));

        // When: the expired monitor is processed (inside the SLA store's transaction)
        List<ExecutorContext> toEmit = processor.processExpired(NOW);

        Assertions.assertThat(toEmit).as("the violation is applied — the labels land on the execution").hasSize(1);
        assertThat(toEmit.getFirst()).updatedFrom("SLAViolation");
        Assertions.assertThat(toEmit.getFirst().getExecution().getLabels()).contains(new Label("sla", "violated"));

        // and NOTHING was emitted during processing: the caller
        // (DefaultExecutor#executionSLAMonitorLoop) emits the returned contexts only after
        // processExpired() returns, i.e. after the transaction has committed
        assertThat(harness).emittedNothingElse();
        assertThat(harness).emittedNoKills();
        assertThat(harness).emittedNoLoopEvents();

        // and the monitor is consumed
        assertThat(harness).hasNoPendingSlaMonitors();
    }

    // --- violation behaviors

    static Stream<Arguments> terminalBehaviors() {
        return Stream.of(
            Arguments.of(SLA.Behavior.FAIL, State.Type.FAILED),
            Arguments.of(SLA.Behavior.CANCEL, State.Type.CANCELLED)
        );
    }

    @ParameterizedTest
    @MethodSource("terminalBehaviors")
    void shouldStopExecutionAndRequestKillWhenTerminalSlaViolated(SLA.Behavior behavior, State.Type expectedState) {
        // Given
        FlowWithSource flow = slaFlow(maxDuration(behavior, Duration.ZERO, null));
        harness.registerFlow(flow);
        Execution running = runningExecution(flow);
        harness.slaMonitorStateStore().save(monitor(running));

        // When
        List<ExecutorContext> toEmit = processor.processExpired(NOW);

        Assertions.assertThat(toEmit).as("the execution is stopped in the behavior's state").hasSize(1);
        assertThat(toEmit.getFirst()).executionInState(expectedState).updatedFrom("SLAViolation");

        // and the kill request IS emitted from inside the execution lock — the known residual
        // outbox violation of ExecutorService#processViolation (shared with the
        // execution-changed SLA path), pinned here until it gets its own seam
        Assertions.assertThat(harness.kills()).hasSize(1);
    }

    @Test
    void shouldReturnUntouchedContextWhenExpiredSlaIsNotViolated() {
        // Given: the monitor deadline passed but the SLA itself is satisfied (a day of budget)
        FlowWithSource flow = slaFlow(maxDuration(SLA.Behavior.FAIL, Duration.ofDays(1), null));
        harness.registerFlow(flow);
        Execution running = runningExecution(flow);
        harness.slaMonitorStateStore().save(monitor(running));

        // When
        List<ExecutorContext> toEmit = processor.processExpired(NOW);

        Assertions.assertThat(toEmit).as("the context comes back unchanged — nothing to apply, nothing killed").hasSize(1);
        Assertions.assertThat(toEmit.getFirst().isExecutionUpdated()).isFalse();
        assertThat(harness).emittedNoKills();
        assertThat(harness).hasNoPendingSlaMonitors();
    }

    // --- monitors that must be ignored

    @Test
    void shouldIgnoreMonitorWhenSlaWasRemovedFromFlow() {
        // Given: the flow was updated and no longer defines the monitored SLA
        FlowWithSource flow = Flows.of(Flows.log());
        harness.registerFlow(flow);
        Execution running = runningExecution(flow);
        harness.slaMonitorStateStore().save(monitor(running));

        // When
        List<ExecutorContext> toEmit = processor.processExpired(NOW);

        Assertions.assertThat(toEmit).as("nothing to process, the stale monitor is still consumed").as("nothing popped").isEmpty();
        assertThat(harness).emittedNoKills();
        assertThat(harness).hasNoPendingSlaMonitors();
    }

    @Test
    void shouldIgnoreMonitorWhenExecutionAlreadyTerminated() {
        // Given: the race the production comment calls out — the monitor still exists but the
        // execution terminated in the meantime
        FlowWithSource flow = slaFlow(maxDuration(SLA.Behavior.FAIL, Duration.ZERO, null));
        harness.registerFlow(flow);
        Execution terminated = Executions.created(flow).withState(State.Type.RUNNING).withState(State.Type.SUCCESS);
        harness.executionStateStore().save(terminated);
        harness.slaMonitorStateStore().save(monitor(terminated));

        // When
        List<ExecutorContext> toEmit = processor.processExpired(NOW);
        Assertions.assertThat(toEmit).as("nothing popped").isEmpty();
        assertThat(harness).emittedNoKills();
    }

    @Test
    void shouldLeaveMonitorsPendingBeforeTheirDeadline() {
        // Given: a monitor whose deadline is still in the future
        FlowWithSource flow = slaFlow(maxDuration(SLA.Behavior.FAIL, Duration.ZERO, null));
        harness.registerFlow(flow);
        Execution running = runningExecution(flow);
        harness.slaMonitorStateStore().save(
            SLAMonitor.builder().executionId(running.getId()).slaId("sla").deadline(NOW.plusSeconds(60)).build()
        );

        // When
        List<ExecutorContext> toEmit = processor.processExpired(NOW);

        Assertions.assertThat(toEmit).as("untouched").isEmpty();
        Assertions.assertThat(harness.slaMonitorStateStore().pending()).hasSize(1);
    }

    // --- fixtures

    private Execution runningExecution(FlowWithSource flow) {
        Execution running = Executions.created(flow).withState(State.Type.RUNNING);
        harness.executionStateStore().save(running);
        return running;
    }

    private static SLAMonitor monitor(Execution execution) {
        return SLAMonitor.builder()
            .executionId(execution.getId())
            .slaId("sla")
            .deadline(NOW.minusSeconds(1))
            .build();
    }

    private static FlowWithSource slaFlow(SLA sla) {
        return Flows.of(Flows.builder(Flows.log()).sla(List.of(sla)).build());
    }

    /**
     * A MAX_DURATION SLA: {@code Duration.ZERO} is always already violated (the execution's
     * start date is stamped at creation, so any elapsed time exceeds it) — keeps the tests
     * deterministic without injectable time.
     */
    private static MaxDurationSLA maxDuration(SLA.Behavior behavior, Duration duration, List<Label> labels) {
        return MaxDurationSLA.builder()
            .id("sla")
            .type(SLA.Type.MAX_DURATION)
            .behavior(behavior)
            .duration(duration)
            .labels(labels)
            .build();
    }
}
