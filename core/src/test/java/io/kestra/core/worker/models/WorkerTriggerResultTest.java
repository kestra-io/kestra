package io.kestra.core.worker.models;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.flows.State;
import io.kestra.core.models.triggers.TriggerEvaluationResult;
import io.kestra.core.models.triggers.TriggerId;
import io.kestra.core.scheduler.model.TriggerType;

import static org.assertj.core.api.Assertions.assertThat;

class WorkerTriggerResultTest {

    private static final TriggerId TRIGGER_ID = TriggerId.of("tenant", "namespace", "flow", "trigger");

    @Test
    void shouldBeTerminalWhenRealtimeResultHasNoEvaluation() {
        assertThat(result(TriggerType.REALTIME, null).isTerminal()).isTrue();
    }

    @Test
    void shouldBeTerminalWhenRealtimeEvaluationIsFailed() {
        assertThat(result(TriggerType.REALTIME, evaluation(State.Type.FAILED)).isTerminal()).isTrue();
    }

    @Test
    void shouldNotBeTerminalWhenRealtimeEvaluationIsCreated() {
        assertThat(result(TriggerType.REALTIME, evaluation(State.Type.CREATED)).isTerminal()).isFalse();
    }

    @Test
    void shouldBeTerminalWhenPollingEvaluationIsCreated() {
        assertThat(result(TriggerType.POLLING, evaluation(State.Type.CREATED)).isTerminal()).isTrue();
    }

    private static WorkerTriggerResult result(TriggerType type, TriggerEvaluationResult evaluation) {
        return new WorkerTriggerResult(TRIGGER_ID, type, evaluation, 1L);
    }

    private static TriggerEvaluationResult evaluation(State.Type stateType) {
        return new TriggerEvaluationResult("execution-id", stateType, null, null, null, null, null);
    }
}
