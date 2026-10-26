package io.kestra.core.services;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.kestra.core.async.AsyncOperationsConfiguration;
import io.kestra.core.exceptions.KestraRuntimeException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionTrigger;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.triggers.TriggerId;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.repositories.CheckpointRepositoryInterface;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.LogDataStoreInterface;
import io.kestra.core.repositories.MetricRepositoryInterface;
import io.kestra.core.runners.FlowInputOutput;
import io.kestra.core.scheduler.events.TriggerEvent;
import io.kestra.core.scheduler.events.TriggerExecutionDeleted;
import io.kestra.core.scheduler.queue.TriggerEventQueue;
import io.kestra.core.storages.StorageInterface;
import io.kestra.plugin.core.trigger.Schedule;
import io.kestra.plugin.core.trigger.Webhook;

import io.micronaut.context.event.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ExecutionServiceDeleteTest {

    private TriggerEventQueue triggerEventQueue;
    private LogDataStoreInterface logRepository;
    private ExecutionService executionService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        triggerEventQueue = mock(TriggerEventQueue.class);
        logRepository = mock(LogDataStoreInterface.class);
        executionService = new ExecutionService(
            mock(StorageInterface.class),
            mock(ExecutionRepositoryInterface.class),
            logRepository,
            mock(MetricRepositoryInterface.class),
            mock(FlowInputOutput.class),
            mock(ApplicationEventPublisher.class),
            mock(ConcurrencyLimitService.class),
            mock(FlowParsingService.class),
            mock(TaskOutputService.class),
            mock(ExecutionOutputService.class),
            mock(CheckpointRepositoryInterface.class),
            mock(DispatchQueueInterface.class),
            mock(BroadcastQueueInterface.class),
            mock(DispatchQueueInterface.class),
            mock(AsyncOperationWaiter.class),
            mock(AsyncOperationsConfiguration.class),
            triggerEventQueue,
            Optional.empty()
        );
    }

    private static Execution execution(State.Type state, String triggerType) {
        return Execution.builder()
            .id("execution")
            .tenantId("main")
            .namespace("io.kestra.tests")
            .flowId("flow")
            .trigger(ExecutionTrigger.builder().id("trigger").type(triggerType).build())
            .state(new State())
            .build()
            .withState(state);
    }

    @Test
    void shouldReleaseTriggerLockWhenDeletingRunningExecution() throws Exception {
        // Given
        Execution execution = execution(State.Type.RUNNING, Schedule.class.getName());

        // When
        executionService.delete(execution, false, false, false);

        // Then
        ArgumentCaptor<TriggerEvent> captor = ArgumentCaptor.forClass(TriggerEvent.class);
        verify(triggerEventQueue).send(captor.capture());
        assertThat(captor.getValue()).isInstanceOfSatisfying(TriggerExecutionDeleted.class, event ->
        {
            assertThat(event.id()).isEqualTo(TriggerId.of("main", "io.kestra.tests", "flow", "trigger"));
            assertThat(event.executionId()).isEqualTo("execution");
        });
    }

    @Test
    void shouldNotReleaseTriggerLockWhenDeletingTerminatedExecution() throws Exception {
        // Given
        Execution execution = execution(State.Type.SUCCESS, Schedule.class.getName());

        // When
        executionService.delete(execution, false, false, false);

        // Then
        verify(triggerEventQueue, never()).send(any());
    }

    @Test
    void shouldNotReleaseTriggerLockWhenDeletingExecutionOfUnmanagedTrigger() throws Exception {
        // Given
        Execution execution = execution(State.Type.RUNNING, Webhook.class.getName());

        // When
        executionService.delete(execution, false, false, false);

        // Then
        verify(triggerEventQueue, never()).send(any());
    }

    @Test
    void shouldCompleteDeletionWhenTriggerLockReleaseFails() throws Exception {
        // Given
        Execution execution = execution(State.Type.RUNNING, Schedule.class.getName());
        doThrow(new KestraRuntimeException("queue unavailable")).when(triggerEventQueue).send(any());

        // When
        executionService.delete(execution, true, false, false);

        // Then
        verify(logRepository).purge(execution);
    }
}
