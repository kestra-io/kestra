package io.kestra.executor.handler;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.kestra.core.executor.command.Create;
import io.kestra.core.executor.command.ExecutionCommand;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionTrigger;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionStateStore;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.runners.MultipleConditionEvent;
import io.kestra.core.scheduler.events.UnscheduledTriggerFired;
import io.kestra.core.scheduler.queue.TriggerEventQueue;
import io.kestra.executor.FlowTriggerService;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@KestraTest
class MultipleConditionEventMessageHandlerTest {
    @Inject
    private MultipleConditionEventMessageHandler multipleConditionEventMessageHandler;

    @Inject
    private ExecutionRepositoryInterface executionRepository;

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Test
    void shouldHandleAMessage() {
        var flow = Fixtures.flow();
        flowRepository.create(GenericFlow.of(flow));
        var execution = Execution.newExecution(flow, Collections.emptyList());
        executionRepository.save(execution);
        var multipleConditionEvent = new MultipleConditionEvent(flow, execution);

        multipleConditionEventMessageHandler.handle(multipleConditionEvent);
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldCarryExecutionDepthAcrossToTheCreateCommand() throws QueueException {
        // Given a trigger evaluation that already carries a non-zero execution depth — this is the
        // hop MultipleConditionEventMessageHandler must not drop when it rebuilds the Execution
        // returned by FlowTriggerService into a Create command (see Flow.evaluate/FlowTriggerService,
        // which is where the depth is actually computed; the mock here just stands in for that)
        var flow = Fixtures.flow();
        var upstream = Execution.newExecution(flow, Collections.emptyList());
        var triggered = Execution.newExecution(flow, Collections.emptyList());
        triggered = triggered.toBuilder()
            .metadata(triggered.getMetadata().withExecutionDepth(4))
            .trigger(ExecutionTrigger.of(flowTrigger(), Map.of()))
            .build();

        FlowTriggerService flowTriggerService = mock(FlowTriggerService.class);
        when(flowTriggerService.computeExecutionsFromFlowTriggerDependsOn(any(), any(), any())).thenReturn(List.of(triggered));
        MultipleConditionStateStore multipleConditionStateStore = mock(MultipleConditionStateStore.class);
        DispatchQueueInterface<ExecutionCommand> executionCommandQueue = mock(DispatchQueueInterface.class);
        TriggerEventQueue triggerEventQueue = mock(TriggerEventQueue.class);
        var handler = new MultipleConditionEventMessageHandler(
            flowTriggerService, multipleConditionStateStore, executionCommandQueue, triggerEventQueue
        );

        // When
        handler.handle(new MultipleConditionEvent(flow, upstream));

        // Then the Create command carries the depth onward, rather than resetting it to a fresh root
        ArgumentCaptor<ExecutionCommand> captor = ArgumentCaptor.forClass(ExecutionCommand.class);
        verify(executionCommandQueue).emit(captor.capture());
        assertThat(((Create) captor.getValue()).executionDepth()).isEqualTo(4);
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldEmitUnscheduledTriggerFiredForEachExecutionCreatedByAFlowTrigger() {
        // Given a dependsOn flow trigger that matched
        var flow = Fixtures.flow();
        var upstream = Execution.newExecution(flow, Collections.emptyList());
        var triggered = Execution.newExecution(flow, Collections.emptyList())
            .toBuilder()
            .trigger(ExecutionTrigger.of(flowTrigger(), Map.of()))
            .build();

        FlowTriggerService flowTriggerService = mock(FlowTriggerService.class);
        when(flowTriggerService.computeExecutionsFromFlowTriggerDependsOn(any(), any(), any())).thenReturn(List.of(triggered));
        DispatchQueueInterface<ExecutionCommand> executionCommandQueue = mock(DispatchQueueInterface.class);
        TriggerEventQueue triggerEventQueue = mock(TriggerEventQueue.class);
        var handler = new MultipleConditionEventMessageHandler(
            flowTriggerService, mock(MultipleConditionStateStore.class), executionCommandQueue, triggerEventQueue
        );

        // When
        handler.handle(new MultipleConditionEvent(flow, upstream));

        // Then the scheduler is told the trigger fired, so it can record it on the trigger state
        ArgumentCaptor<UnscheduledTriggerFired> captor = ArgumentCaptor.forClass(UnscheduledTriggerFired.class);
        verify(triggerEventQueue).send(captor.capture());
        assertThat(captor.getValue().executionId()).isEqualTo(triggered.getId());
        assertThat(captor.getValue().id().getTriggerId()).isEqualTo("flow-trigger");
    }

    private static io.kestra.plugin.core.trigger.Flow flowTrigger() {
        return io.kestra.plugin.core.trigger.Flow.builder()
            .id("flow-trigger")
            .type(io.kestra.plugin.core.trigger.Flow.class.getName())
            .build();
    }
}
