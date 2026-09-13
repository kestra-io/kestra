package io.kestra.controller.grpc.services;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.flows.State;
import io.kestra.core.models.triggers.TriggerEvaluationResult;

import static org.assertj.core.api.Assertions.assertThat;

class GrpcWorkerControllerServiceTest {

    @Test
    void shouldReleaseRunningEntryWhenRealtimeResultHasNoEvaluation() {
        assertThat(GrpcWorkerControllerService.isTerminalRealtimeResult(null)).isTrue();
    }

    @Test
    void shouldReleaseRunningEntryWhenRealtimeEvaluationIsFailed() {
        assertThat(GrpcWorkerControllerService.isTerminalRealtimeResult(evaluation(State.Type.FAILED))).isTrue();
    }

    @Test
    void shouldKeepRunningEntryWhenRealtimeEvaluationIsCreated() {
        assertThat(GrpcWorkerControllerService.isTerminalRealtimeResult(evaluation(State.Type.CREATED))).isFalse();
    }

    private static TriggerEvaluationResult evaluation(State.Type stateType) {
        return new TriggerEvaluationResult("execution-id", stateType, null, null, null, null, null);
    }
}
