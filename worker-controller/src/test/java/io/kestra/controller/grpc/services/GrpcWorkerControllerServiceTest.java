package io.kestra.controller.grpc.services;

import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;

import io.kestra.core.executor.WorkerJobRunningStateStore;
import io.kestra.core.exceptions.KestraRuntimeException;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.triggers.TriggerEvaluationResult;
import io.kestra.core.models.triggers.TriggerId;
import io.kestra.core.queues.MessageTooBigException;
import io.kestra.core.queues.UnsupportedMessageException;
import io.kestra.core.runners.NoTransactionContext;
import io.kestra.core.scheduler.events.TriggerEvaluated;
import io.kestra.core.scheduler.queue.TriggerEventQueue;
import io.kestra.core.scheduler.model.TriggerType;
import io.kestra.core.worker.models.WorkerTriggerResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class GrpcWorkerControllerServiceTest {

    @Test
    void shouldPublishNullEvaluationWhenUnsupportedPollingResultIsRejected() throws Exception {
        TriggerEventQueue queue = mock(TriggerEventQueue.class);
        WorkerJobRunningStateStore runningStateStore = mock(WorkerJobRunningStateStore.class);
        GrpcWorkerControllerService service = service(queue, runningStateStore);
        TriggerId triggerId = triggerId();
        WorkerTriggerResult result = result(triggerId);
        doThrow(new KestraRuntimeException("queue rejected payload", new UnsupportedMessageException("unsupported payload", new IllegalArgumentException("unsupported value type"))))
            .doNothing().when(queue).send(any());

        service.processPollingTriggerResult(result);

        org.mockito.ArgumentCaptor<TriggerEvaluated> events = org.mockito.ArgumentCaptor.forClass(TriggerEvaluated.class);
        verify(queue, org.mockito.Mockito.times(2)).send(events.capture());
        assertThat(events.getAllValues().get(1).id()).isEqualTo(triggerId);
        assertThat(events.getAllValues().get(1).evaluation()).isNull();
        assertThat(events.getAllValues().get(1).eventId()).isNotNull();
        verify(runningStateStore).deleteByKey(NoTransactionContext.INSTANCE, triggerId.uid());
    }

    @Test
    void shouldPropagateUnrelatedQueueFailureWithoutPublishingFallback() throws Exception {
        TriggerEventQueue queue = mock(TriggerEventQueue.class);
        WorkerJobRunningStateStore runningStateStore = mock(WorkerJobRunningStateStore.class);
        GrpcWorkerControllerService service = service(queue, runningStateStore);
        TriggerId triggerId = triggerId();
        doThrow(new KestraRuntimeException("queue unavailable", new IllegalStateException("database unavailable")))
            .when(queue).send(any());

        assertThatThrownBy(() -> service.processPollingTriggerResult(result(triggerId)))
            .isInstanceOf(KestraRuntimeException.class)
            .hasMessage("queue unavailable");
        verify(queue).send(any());
        verify(runningStateStore, never()).deleteByKey(eq(NoTransactionContext.INSTANCE), eq(triggerId.uid()));
    }

    @Test
    void shouldRetainRunningEntryWhenNullFallbackCannotBePublished() throws Exception {
        TriggerEventQueue queue = mock(TriggerEventQueue.class);
        WorkerJobRunningStateStore runningStateStore = mock(WorkerJobRunningStateStore.class);
        GrpcWorkerControllerService service = service(queue, runningStateStore);
        TriggerId triggerId = triggerId();
        doThrow(new KestraRuntimeException("too big", new MessageTooBigException("limit")))
            .doThrow(new KestraRuntimeException("fallback failed"))
            .when(queue).send(any());

        assertThatThrownBy(() -> service.processPollingTriggerResult(result(triggerId)))
            .isInstanceOf(KestraRuntimeException.class)
            .hasMessage("fallback failed");
        verify(runningStateStore, never()).deleteByKey(eq(NoTransactionContext.INSTANCE), eq(triggerId.uid()));
    }

    private static GrpcWorkerControllerService service(TriggerEventQueue queue, WorkerJobRunningStateStore runningStateStore) throws Exception {
        GrpcWorkerControllerService service = new GrpcWorkerControllerService();
        setField(service, "triggerEventQueue", queue);
        setField(service, "workerJobRunningStateStore", runningStateStore);
        return service;
    }

    private static TriggerId triggerId() {
        return TriggerId.of("tenant", "namespace", "flow", "trigger");
    }

    private static WorkerTriggerResult result(TriggerId triggerId) {
        return new WorkerTriggerResult(triggerId, TriggerType.POLLING, evaluation(), 0L);
    }

    private static TriggerEvaluationResult evaluation() {
        return new TriggerEvaluationResult("execution-id", State.Type.CREATED, null, null, null, null, null);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = GrpcWorkerControllerService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
