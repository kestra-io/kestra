package io.kestra.executor.handler;

import java.util.Collections;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.runners.SubflowExecutionEnd;

import jakarta.inject.Inject;

@KestraTest
class SubflowExecutionEndMessageHandlerTest {
    @Inject
    private SubflowExecutionEndMessageHandler subflowExecutionEndMessageHandler;

    @Inject
    private ExecutionRepositoryInterface executionRepository;

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Test
    void shouldHandleAMessage() {
        var flow = flowRepository.create(GenericFlow.of(Fixtures.flow()));
        var parentExecution = Execution.newExecution(flow, Collections.emptyList());
        executionRepository.save(parentExecution);
        var execution = Execution.newExecution(flow, Collections.emptyList());
        executionRepository.save(execution);

        var subflowExecutionEnd = new SubflowExecutionEnd(
            execution,
            parentExecution.getId(),
            "task",
            "taskRun",
            State.Type.SUCCESS
        );

        subflowExecutionEndMessageHandler.handle(subflowExecutionEnd);
    }
}