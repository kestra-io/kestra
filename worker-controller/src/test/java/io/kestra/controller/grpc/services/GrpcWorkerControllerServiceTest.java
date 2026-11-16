package io.kestra.controller.grpc.services;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.flows.State;
import io.kestra.core.models.triggers.TriggerEvaluationResult;
import io.kestra.core.models.triggers.TriggerId;
import io.kestra.core.scheduler.events.TriggerExecutionTerminated;
import io.kestra.core.scheduler.model.TriggerType;
import io.kestra.core.worker.models.WorkerTriggerResult;

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

    @Test
    void shouldUnlockWithDispatchEpochWhenRealtimeTriggerFailsToStart() {
        TriggerId triggerId = TriggerId.of("main", "company.team", "flow", "realtime");
        WorkerTriggerResult result = new WorkerTriggerResult(triggerId, TriggerType.REALTIME, evaluation(State.Type.FAILED), 42L);

        TriggerExecutionTerminated event = GrpcWorkerControllerService.realtimeTerminationEvent(result);

        assertThat(event.dispatchEpoch()).isEqualTo(42L);
        assertThat(event.executionId()).isEqualTo("execution-id");
        assertThat(event.executionState()).isEqualTo(State.Type.FAILED);
    }

    private static TriggerEvaluationResult evaluation(State.Type stateType) {
        return new TriggerEvaluationResult("execution-id", stateType, null, null, null, null, null);
    }
}
