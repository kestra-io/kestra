package io.kestra.core.models.triggers;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;

import static org.assertj.core.api.Assertions.assertThat;

class TriggerEvaluationResultTest {

    @Test
    void shouldRetainTraceParentWhenCreatedFromExecution() {
        String traceParent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
        Execution execution = Execution.builder()
            .id("exec-1")
            .tenantId("tenant-1")
            .namespace("ns-1")
            .flowId("flow-1")
            .flowRevision(1)
            .state(new State())
            .traceParent(traceParent)
            .build();

        TriggerEvaluationResult result = TriggerEvaluationResult.from(execution);
        assertThat(result.traceParent()).isEqualTo(traceParent);

        TriggerId triggerId = TriggerId.of("tenant-1", "ns-1", "flow-1", "trig-1");
        Execution reconstructed = result.toExecution(triggerId);
        assertThat(reconstructed.getTraceParent()).isEqualTo(traceParent);
    }

    @Test
    void shouldRetainTraceParentWhenWithStateApplied() {
        String traceParent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
        TriggerEvaluationResult result = new TriggerEvaluationResult(
            "exec-1",
            State.Type.CREATED,
            null,
            null,
            1,
            null,
            null
        ).withTraceParent(traceParent);

        TriggerEvaluationResult updated = result.withState(State.Type.RUNNING);
        assertThat(updated.traceParent()).isEqualTo(traceParent);

        TriggerId triggerId = TriggerId.of("tenant-1", "ns-1", "flow-1", "trig-1");
        Execution execution = updated.toExecution(triggerId);
        assertThat(execution.getTraceParent()).isEqualTo(traceParent);
    }
}
