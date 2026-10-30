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
        var sla = MaxDurationSLA.builder().id("max-duration").type(SLA.Type.MAX_DURATION).behavior(SLA.Behavior.FAIL).duration(Duration.ofHours(1)).build();
        FlowWithSource flow = Flows.of(Flows.builder(Flows.log()).sla(List.of(sla)).build());
        harness.registerFlow(flow);
        Execution execution = Executions.created(flow);
        harness.step(execution);

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
        harness.slaMonitorProcessor().processExpired(Instant.now().plus(Duration.ofHours(2)));

        // Then: the duration is derived from the deadline, so a fresh execution does not violate its SLA
        assertThat(harness.stateOf(execution)).isNotEqualTo(State.Type.FAILED);
    }
}
