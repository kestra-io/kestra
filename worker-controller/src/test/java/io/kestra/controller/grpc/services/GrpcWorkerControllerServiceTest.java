package io.kestra.controller.grpc.services;

import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.event.Level;

import io.kestra.core.exceptions.KestraRuntimeException;
import io.kestra.core.executor.WorkerJobRunningStateStore;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.triggers.TriggerEvaluationResult;
import io.kestra.core.models.triggers.TriggerId;
import io.kestra.core.queues.MessageTooBigException;
import io.kestra.core.queues.UnsupportedMessageException;
import io.kestra.core.runners.LogEntryEmitter;
import io.kestra.core.runners.NoTransactionContext;
import io.kestra.core.scheduler.events.TriggerEvaluated;
import io.kestra.core.scheduler.model.TriggerType;
import io.kestra.core.scheduler.queue.TriggerEventQueue;
import io.kestra.core.scheduler.service.UnpublishableTriggerResultReporter;
import io.kestra.core.worker.models.WorkerTriggerResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class GrpcWorkerControllerServiceTest {

    @Test
    void shouldPropagateQueueFailureWhenCauseIsNotMessageSize() throws Exception {
        // Given
        TriggerEventQueue queue = mock(TriggerEventQueue.class);
        WorkerJobRunningStateStore runningStateStore = mock(WorkerJobRunningStateStore.class);
        GrpcWorkerControllerService service = service(queue, runningStateStore);
        TriggerId triggerId = triggerId();
        doThrow(new KestraRuntimeException("queue unavailable", new IllegalStateException("database unavailable")))
            .when(queue).send(any());

        // When / Then
        assertThatThrownBy(() -> service.processPollingTriggerResult(result(triggerId)))
            .isInstanceOf(KestraRuntimeException.class)
            .hasMessage("queue unavailable");
        verify(queue).send(any());
        verify(runningStateStore, never()).deleteByKey(NoTransactionContext.INSTANCE, triggerId.uid());
    }

    @Test
    void shouldRetainRunningEntryWhenNullEvaluationCannotBePublished() throws Exception {
        // Given
        TriggerEventQueue queue = mock(TriggerEventQueue.class);
        WorkerJobRunningStateStore runningStateStore = mock(WorkerJobRunningStateStore.class);
        GrpcWorkerControllerService service = service(queue, runningStateStore);
        TriggerId triggerId = triggerId();
        doThrow(new KestraRuntimeException("too big", new MessageTooBigException("limit")))
            .doThrow(new KestraRuntimeException("fallback failed"))
            .when(queue).send(any());

        // When / Then
        assertThatThrownBy(() -> service.processPollingTriggerResult(result(triggerId)))
            .isInstanceOf(KestraRuntimeException.class)
            .hasMessage("fallback failed");
        verify(runningStateStore, never()).deleteByKey(NoTransactionContext.INSTANCE, triggerId.uid());
    }

    @Test
    void shouldUnlockTriggerAndLogToTriggerLogsWhenResultIsUnsupported() throws Exception {
        // Given
        TriggerEventQueue queue = mock(TriggerEventQueue.class);
        WorkerJobRunningStateStore runningStateStore = mock(WorkerJobRunningStateStore.class);
        LogEntryEmitter logEntryEmitter = mock(LogEntryEmitter.class);
        GrpcWorkerControllerService service = service(queue, runningStateStore, logEntryEmitter);
        TriggerId triggerId = triggerId();
        doThrow(new KestraRuntimeException("publish failed", new UnsupportedMessageException("unsupported unicode escape sequence", null)))
            .doNothing()
            .when(queue).send(any());

        // When
        service.processPollingTriggerResult(result(triggerId));

        // Then
        ArgumentCaptor<TriggerEvaluated> sent = ArgumentCaptor.forClass(TriggerEvaluated.class);
        verify(queue, times(2)).send(sent.capture());
        assertThat(sent.getAllValues().getLast().evaluation()).isNull();
        verify(runningStateStore).deleteByKey(NoTransactionContext.INSTANCE, triggerId.uid());

        ArgumentCaptor<LogEntry> logged = ArgumentCaptor.forClass(LogEntry.class);
        verify(logEntryEmitter).emits(logged.capture());
        assertThat(logged.getValue().getTriggerId()).isEqualTo("trigger");
        assertThat(logged.getValue().getFlowId()).isEqualTo("flow");
        assertThat(logged.getValue().getLevel()).isEqualTo(Level.ERROR);
        assertThat(logged.getValue().getMessage()).contains("unsupported unicode escape sequence");
    }

    private static GrpcWorkerControllerService service(TriggerEventQueue queue, WorkerJobRunningStateStore runningStateStore) throws Exception {
        return service(queue, runningStateStore, mock(LogEntryEmitter.class));
    }

    private static GrpcWorkerControllerService service(TriggerEventQueue queue, WorkerJobRunningStateStore runningStateStore, LogEntryEmitter logEntryEmitter) throws Exception {
        GrpcWorkerControllerService service = new GrpcWorkerControllerService();
        setField(service, "triggerEventQueue", queue);
        setField(service, "workerJobRunningStateStore", runningStateStore);
        setField(service, "unpublishableTriggerResultReporter", new UnpublishableTriggerResultReporter(logEntryEmitter));
        return service;
    }

    private static TriggerId triggerId() {
        return TriggerId.of("tenant", "namespace", "flow", "trigger");
    }

    private static WorkerTriggerResult result(TriggerId triggerId) {
        return new WorkerTriggerResult(triggerId, TriggerType.POLLING, evaluation(State.Type.CREATED), 0L);
    }

    private static TriggerEvaluationResult evaluation(State.Type stateType) {
        return new TriggerEvaluationResult("execution-id", stateType, null, null, null, null, null);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = GrpcWorkerControllerService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
