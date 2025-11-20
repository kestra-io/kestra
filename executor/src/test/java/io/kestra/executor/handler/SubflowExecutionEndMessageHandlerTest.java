package io.kestra.executor.handler;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.runners.SubflowExecutionEnd;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Collections;

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
        var flow = Fixtures.flow();
        flowRepository.create(GenericFlow.of(flow));
        var parentExecution = Execution.newExecution(flow, Collections.emptyList());
        executionRepository.save(parentExecution);
        var execution = Execution.newExecution(flow, Collections.emptyList());
        executionRepository.save(execution);

        var subflowExecutionEnd = SubflowExecutionEnd
            .builder()
            .childExecution(execution)
            .parentExecutionId(parentExecution.getId())
            .state(State.Type.SUCCESS)
            .taskId("task")
            .taskRunId("taskRun")
            .build();

        subflowExecutionEndMessageHandler.handle(subflowExecutionEnd);
    }
}