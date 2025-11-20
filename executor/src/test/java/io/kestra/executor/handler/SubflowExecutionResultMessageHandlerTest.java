package io.kestra.executor.handler;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.runners.SubflowExecutionResult;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class SubflowExecutionResultMessageHandlerTest {
    @Inject
    private SubflowExecutionResultMessageHandler subflowExecutionResultMessageHandler;

    @Inject
    private ExecutionRepositoryInterface executionRepository;

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Test
    void shouldReturnEmptyForNonExistingExecution() {
        var subflowExecutionResult = SubflowExecutionResult.builder()
            .executionId("execution")
            .state(State.Type.SUCCESS)
            .parentTaskRun(TaskRun.builder()
                .id("parent")
                .flowId("flow")
                .namespace("namespace")
                .executionId("execution")
                .build())
            .build();

        var maybeExecutor = subflowExecutionResultMessageHandler.handle(subflowExecutionResult);

        assertThat(maybeExecutor).isEmpty();
    }

    @Test
    void shouldReturnAnExecutorForExistingExecution() {
        var flow = Fixtures.flow();
        flowRepository.create(GenericFlow.of(flow));
        var parentExecution = Execution.newExecution(flow, Collections.emptyList());
        executionRepository.save(parentExecution);
        var execution = Execution.newExecution(flow, Collections.emptyList());
        executionRepository.save(execution);
        var subflowExecutionResult = SubflowExecutionResult.builder()
            .executionId(execution.getId())
            .state(State.Type.SUCCESS)
            .parentTaskRun(TaskRun.builder()
                .id("parent")
                .flowId(parentExecution.getFlowId())
                .namespace(parentExecution.getNamespace())
                .executionId(parentExecution.getId())
                .build())
            .build();

        var maybeExecutor = subflowExecutionResultMessageHandler.handle(subflowExecutionResult);

        assertThat(maybeExecutor).isPresent();
    }
}