package io.kestra.executor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.flows.sla.SLA;
import io.kestra.core.models.flows.sla.SLAMonitor;
import io.kestra.core.models.flows.sla.types.MaxDurationSLA;
import io.kestra.executor.testkit.Executions;
import io.kestra.executor.testkit.ExecutorTestHarness;
import io.kestra.executor.testkit.Flows;

import static org.assertj.core.api.Assertions.assertThat;

class SLAMonitorProcessorTest {
    private final ExecutorTestHarness harness = ExecutorTestHarness.create();

    @Test
    void shouldEvaluateMonitorPersistedWithoutDuration() {
        // Given: a monitor saved by a previous version, which had no duration
        Execution execution = runningExecutionWithMaxDuration(Duration.ofHours(1), Instant.now());
        SLAMonitor monitor = harness.slaMonitorStateStore().pending().getFirst();
        harness.slaMonitorStateStore().purge(execution.getId());
        harness.slaMonitorStateStore().save(
            SLAMonitor.builder()
                .executionId(monitor.getExecutionId())
                .slaId(monitor.getSlaId())
                .deadline(monitor.getDeadline())
                .build()
        );

        // When
        List<ExecutorContext> processed = harness.slaMonitorProcessor().processExpired(Instant.now().plus(Duration.ofHours(2)));

        // Then: the duration is derived from the deadline, so a fresh execution does not violate its SLA
        assertThat(processed).as("the expired monitor was evaluated").hasSize(1);
        assertThat(processed.getFirst().getExecution().getState().getCurrent()).as("no violation for a fresh execution").isEqualTo(State.Type.RUNNING);
        assertThat(harness.stateOf(execution)).isEqualTo(State.Type.RUNNING);
        assertThat(harness.slaMonitorStateStore().pending()).as("the monitor is consumed").isEmpty();
    }

    @Test
    void shouldFailARunningExecutionWhoseMaxDurationIsExceeded() {
        // Given
        Execution execution = runningExecutionWithMaxDuration(Duration.ofMinutes(1), Instant.now().minus(Duration.ofHours(1)));

        // When
        List<ExecutorContext> processed = harness.slaMonitorProcessor().processExpired(Instant.now().plus(Duration.ofMinutes(1)));

        // Then
        assertThat(processed).hasSize(1);
        assertThat(processed.getFirst().getExecution().getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(harness.stateOf(execution)).as("the violation is persisted").isEqualTo(State.Type.FAILED);
    }

    @Test
    void shouldApplyTheSlaTheMonitorWasSavedFor() {
        // Given: two SLAs on the flow, and only the monitor of the second one is pending
        var lenient = MaxDurationSLA.builder().id("lenient").type(SLA.Type.MAX_DURATION).behavior(SLA.Behavior.FAIL).duration(Duration.ofDays(1)).build();
        var strict = MaxDurationSLA.builder().id("strict").type(SLA.Type.MAX_DURATION).behavior(SLA.Behavior.CANCEL).duration(Duration.ofMinutes(1)).build();
        FlowWithSource flow = Flows.of(Flows.builder(Flows.log()).sla(List.of(lenient, strict)).build());
        harness.registerFlow(flow);
        Execution execution = Executions.createdAt(flow, Instant.now().minus(Duration.ofHours(1)));
        harness.step(execution);
        SLAMonitor strictMonitor = harness.slaMonitorStateStore().pending().stream().filter(monitor -> "strict".equals(monitor.getSlaId())).findFirst().orElseThrow();
        harness.slaMonitorStateStore().purge(execution.getId());
        harness.slaMonitorStateStore().save(strictMonitor);

        // When
        harness.slaMonitorProcessor().processExpired(Instant.now().plus(Duration.ofMinutes(1)));

        // Then
        assertThat(harness.stateOf(execution)).as("the strict SLA's CANCEL behavior applies").isEqualTo(State.Type.CANCELLED);
    }

    @Test
    void shouldIgnoreAMonitorWhoseSlaWasRemovedFromTheFlow() {
        // Given: the flow was updated and its SLA removed after the monitor was saved
        Execution execution = runningExecutionWithMaxDuration(Duration.ofMinutes(1), Instant.now().minus(Duration.ofHours(1)));
        harness.registerFlow(Flows.of(Flows.builder(Flows.log()).id(execution.getFlowId()).revision(2).build()));

        // When
        List<ExecutorContext> processed = harness.slaMonitorProcessor().processExpired(Instant.now().plus(Duration.ofMinutes(1)));

        // Then
        assertThat(processed).isEmpty();
        assertThat(harness.stateOf(execution)).isEqualTo(State.Type.RUNNING);
    }

    private Execution runningExecutionWithMaxDuration(Duration duration, Instant startDate) {
        var sla = MaxDurationSLA.builder().id("max-duration").type(SLA.Type.MAX_DURATION).behavior(SLA.Behavior.FAIL).duration(duration).build();
        FlowWithSource flow = Flows.of(Flows.builder(Flows.log()).sla(List.of(sla)).build());
        harness.registerFlow(flow);
        Execution execution = Executions.createdAt(flow, startDate);
        harness.step(execution);
        assertThat(harness.stateOf(execution)).as("given").isEqualTo(State.Type.RUNNING);
        return execution;
    }
}
