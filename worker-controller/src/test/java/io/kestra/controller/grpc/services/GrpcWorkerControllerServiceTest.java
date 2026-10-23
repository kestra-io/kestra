package io.kestra.controller.grpc.services;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.protobuf.ByteString;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.kestra.controller.messages.MessageFormats;
import io.kestra.core.executor.WorkerJobRunningStateStore;
import io.kestra.core.events.EventId;
import io.kestra.core.exceptions.KestraRuntimeException;
import io.kestra.core.models.Label;
import io.kestra.core.models.executions.ExecutionTrigger;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.triggers.TriggerEvaluationResult;
import io.kestra.core.models.triggers.TriggerId;
import io.kestra.core.queues.MessageTooBigException;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.UnsupportedMessageException;
import io.kestra.core.runners.NoTransactionContext;
import io.kestra.core.scheduler.events.TriggerEvaluated;
import io.kestra.core.scheduler.model.TriggerType;
import io.kestra.core.scheduler.queue.TriggerEventQueue;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.worker.models.WorkerTriggerResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

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
    void shouldPublishBoundedFailedEvaluationWhenPollingResultIsTooBig() throws Exception {
        TriggerEventQueue triggerEventQueue = mock(TriggerEventQueue.class);
        WorkerJobRunningStateStore runningStateStore = mock(WorkerJobRunningStateStore.class);
        GrpcWorkerControllerService service = service(triggerEventQueue, runningStateStore);
        TriggerId triggerId = TriggerId.of("tenant", "namespace", "flow", "trigger");
        TriggerEvaluationResult oversized = new TriggerEvaluationResult(
            "stable-execution", State.Type.CREATED,
            ExecutionTrigger.builder().id("trigger").type("test").variables(Map.of("large", "payload")).build(),
            List.of(new Label("large-label", "payload")),
            12, Instant.parse("2026-01-01T00:00:00Z"), Map.of("large", "payload")
        );
        WorkerTriggerResult result = new WorkerTriggerResult(triggerId, TriggerType.POLLING, oversized, 77L);
        doThrow(new KestraRuntimeException("too big", new MessageTooBigException("limit")))
            .doNothing()
            .when(triggerEventQueue).send(any());

        service.processPollingTriggerResult(result);

        ArgumentCaptor<TriggerEvaluated> events = ArgumentCaptor.forClass(TriggerEvaluated.class);
        verify(triggerEventQueue, org.mockito.Mockito.times(2)).send(events.capture());
        TriggerEvaluated original = events.getAllValues().getFirst();
        TriggerEvaluated fallback = events.getAllValues().getLast();
        assertThat(fallback.evaluation().stateType()).isEqualTo(State.Type.FAILED);
        assertThat(fallback.evaluation().executionId()).isEqualTo("stable-execution");
        assertThat(fallback.evaluation().flowRevision()).isEqualTo(12);
        assertThat(fallback.evaluation().scheduleDate()).isEqualTo(oversized.scheduleDate());
        assertThat(fallback.evaluation().trigger()).isNull();
        assertThat(fallback.evaluation().labels()).isNull();
        assertThat(fallback.evaluation().inputs()).isNull();
        assertThat(fallback.dispatchEpoch()).isEqualTo(77L);
        assertThat(fallback.timestamp()).isEqualTo(original.timestamp());
        assertThat(fallback.eventId()).isEqualTo(original.eventId());
        assertThat(fallback.eventId()).isEqualTo(result.resultId());
        verify(runningStateStore).deleteByKey(NoTransactionContext.INSTANCE, triggerId.uid());
    }

    @Test
    void shouldPublishBoundedFailedEvaluationWhenPollingResultIsUnsupported() throws Exception {
        TriggerEventQueue triggerEventQueue = mock(TriggerEventQueue.class);
        WorkerJobRunningStateStore runningStateStore = mock(WorkerJobRunningStateStore.class);
        GrpcWorkerControllerService service = service(triggerEventQueue, runningStateStore);
        TriggerId triggerId = TriggerId.of("tenant", "namespace", "flow", "trigger");
        WorkerTriggerResult result = new WorkerTriggerResult(triggerId, TriggerType.POLLING, evaluation(State.Type.CREATED), 77L);
        UnsupportedMessageException unsupported = new UnsupportedMessageException("unsupported payload", new IllegalArgumentException("invalid value"));
        doThrow(new KestraRuntimeException("queue rejected payload", unsupported))
            .doNothing()
            .when(triggerEventQueue).send(any());

        service.processPollingTriggerResult(result);

        ArgumentCaptor<TriggerEvaluated> events = ArgumentCaptor.forClass(TriggerEvaluated.class);
        verify(triggerEventQueue, org.mockito.Mockito.times(2)).send(events.capture());
        TriggerEvaluated original = events.getAllValues().getFirst();
        TriggerEvaluated fallback = events.getAllValues().getLast();
        assertThat(fallback.evaluation().stateType()).isEqualTo(State.Type.FAILED);
        assertThat(fallback.eventId()).isEqualTo(result.resultId()).isEqualTo(original.eventId());
        assertThat(fallback.timestamp()).isEqualTo(original.timestamp());
        assertThat(fallback.dispatchEpoch()).isEqualTo(77L);
        verify(runningStateStore).deleteByKey(NoTransactionContext.INSTANCE, triggerId.uid());
    }

    @Test
    void shouldNotPublishFailedFallbackForUnrelatedPollingQueueFailure() throws Exception {
        TriggerEventQueue triggerEventQueue = mock(TriggerEventQueue.class);
        WorkerJobRunningStateStore runningStateStore = mock(WorkerJobRunningStateStore.class);
        GrpcWorkerControllerService service = service(triggerEventQueue, runningStateStore);
        TriggerId triggerId = TriggerId.of("tenant", "namespace", "flow", "trigger");
        WorkerTriggerResult result = new WorkerTriggerResult(triggerId, TriggerType.POLLING, evaluation(State.Type.CREATED), 77L);
        KestraRuntimeException queueFailure = new KestraRuntimeException("queue unavailable", new QueueException("connection failed"));
        doThrow(queueFailure).when(triggerEventQueue).send(any());

        assertThatThrownBy(() -> service.processPollingTriggerResult(result)).isSameAs(queueFailure);

        ArgumentCaptor<TriggerEvaluated> event = ArgumentCaptor.forClass(TriggerEvaluated.class);
        verify(triggerEventQueue).send(event.capture());
        assertThat(event.getValue().evaluation().stateType()).isEqualTo(State.Type.CREATED);
        verify(runningStateStore, never()).deleteByKey(eq(NoTransactionContext.INSTANCE), eq(triggerId.uid()));
    }

    @Test
    void shouldRetainRunningEntryWhenFallbackPublicationFails() throws Exception {
        TriggerEventQueue triggerEventQueue = mock(TriggerEventQueue.class);
        WorkerJobRunningStateStore runningStateStore = mock(WorkerJobRunningStateStore.class);
        GrpcWorkerControllerService service = service(triggerEventQueue, runningStateStore);
        TriggerId triggerId = TriggerId.of("tenant", "namespace", "flow", "trigger");
        WorkerTriggerResult result = new WorkerTriggerResult(triggerId, TriggerType.POLLING, evaluation(State.Type.CREATED), 77L);
        doThrow(new KestraRuntimeException("too big", new MessageTooBigException("limit")))
            .doThrow(new KestraRuntimeException("fallback publish failed"))
            .when(triggerEventQueue).send(any());

        assertThatThrownBy(() -> service.processPollingTriggerResult(result))
            .isInstanceOf(KestraRuntimeException.class)
            .hasMessage("fallback publish failed");
        verify(runningStateStore, never()).deleteByKey(eq(NoTransactionContext.INSTANCE), eq(triggerId.uid()));
    }

    @Test
    void shouldNotChangeRealtimeResultHandling() throws Exception {
        TriggerEventQueue triggerEventQueue = mock(TriggerEventQueue.class);
        WorkerJobRunningStateStore runningStateStore = mock(WorkerJobRunningStateStore.class);
        GrpcWorkerControllerService service = service(triggerEventQueue, runningStateStore);

        assertThat(GrpcWorkerControllerService.isTerminalRealtimeResult(null)).isTrue();
        assertThat(GrpcWorkerControllerService.isTerminalRealtimeResult(evaluation(State.Type.FAILED))).isTrue();
        assertThat(GrpcWorkerControllerService.isTerminalRealtimeResult(evaluation(State.Type.CREATED))).isFalse();
        verifyNoInteractions(triggerEventQueue, runningStateStore);
    }

    @Test
    void shouldRoundTripWorkerTriggerResultIdentityAndReadLegacyPayload() throws Exception {
        TriggerId triggerId = TriggerId.of("tenant", "namespace", "flow", "trigger");
        EventId resultId = new EventId(UUID.fromString("0199e800-0000-7000-8000-000000000001"));
        WorkerTriggerResult result = new WorkerTriggerResult(triggerId, TriggerType.POLLING, evaluation(State.Type.CREATED), 77L, resultId);

        WorkerTriggerResult roundTripped = MessageFormats.JSON.fromByteString(
            MessageFormats.JSON.toByteString(result), WorkerTriggerResult.class
        );
        assertThat(roundTripped.resultId()).isEqualTo(resultId);

        JsonNode legacyJson = JacksonMapper.ofJson(false).readTree(MessageFormats.JSON.toByteString(result).toByteArray());
        ((ObjectNode) legacyJson).remove("resultId");
        WorkerTriggerResult legacy = MessageFormats.JSON.fromByteString(
            ByteString.copyFrom(JacksonMapper.ofJson(false).writeValueAsBytes(legacyJson)), WorkerTriggerResult.class
        );
        assertThat(legacy.resultId()).isNull();

        TriggerEventQueue triggerEventQueue = mock(TriggerEventQueue.class);
        WorkerJobRunningStateStore runningStateStore = mock(WorkerJobRunningStateStore.class);
        service(triggerEventQueue, runningStateStore).processPollingTriggerResult(legacy);
        ArgumentCaptor<TriggerEvaluated> event = ArgumentCaptor.forClass(TriggerEvaluated.class);
        verify(triggerEventQueue).send(event.capture());
        assertThat(event.getValue().eventId()).isNotNull();
        assertThat(event.getValue().dispatchEpoch()).isEqualTo(77L);
    }

    private static GrpcWorkerControllerService service(TriggerEventQueue triggerEventQueue, WorkerJobRunningStateStore runningStateStore) throws Exception {
        GrpcWorkerControllerService service = new GrpcWorkerControllerService();
        setField(service, "triggerEventQueue", triggerEventQueue);
        setField(service, "workerJobRunningStateStore", runningStateStore);
        return service;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = GrpcWorkerControllerService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static TriggerEvaluationResult evaluation(State.Type stateType) {
        return new TriggerEvaluationResult("execution-id", stateType, null, null, null, null, null);
    }
}
