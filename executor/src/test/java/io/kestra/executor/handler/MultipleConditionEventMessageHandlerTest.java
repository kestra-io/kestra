package io.kestra.executor.handler;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.kestra.core.executor.command.Create;
import io.kestra.core.executor.command.ExecutionCommand;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionStateStore;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.runners.MultipleConditionEvent;
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
        triggered = triggered.withMetadata(triggered.getMetadata().withExecutionDepth(4));

        FlowTriggerService flowTriggerService = mock(FlowTriggerService.class);
        when(flowTriggerService.computeExecutionsFromFlowTriggerDependsOn(any(), any(), any())).thenReturn(List.of(triggered));
        MultipleConditionStateStore multipleConditionStateStore = mock(MultipleConditionStateStore.class);
        DispatchQueueInterface<ExecutionCommand> executionCommandQueue = mock(DispatchQueueInterface.class);
        var handler = new MultipleConditionEventMessageHandler(flowTriggerService, multipleConditionStateStore, executionCommandQueue);

        // When
        handler.handle(new MultipleConditionEvent(flow, upstream));

        // Then the Create command carries the depth onward, rather than resetting it to a fresh root
        ArgumentCaptor<ExecutionCommand> captor = ArgumentCaptor.forClass(ExecutionCommand.class);
        verify(executionCommandQueue).emit(captor.capture());
        assertThat(((Create) captor.getValue()).executionDepth()).isEqualTo(4);
    }
}