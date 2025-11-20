package io.kestra.executor.handler;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKilled;
import io.kestra.core.models.executions.ExecutionKilledExecution;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.runners.ExecutionEvent;
import io.kestra.core.runners.ExecutionEventType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@KestraTest
class ExecutionKilledExecutionMessageHandlerTest {
    @Inject
    private ExecutionKilledExecutionMessageHandler executionKilledExecutionMessageHandler;

    @Inject
    private ExecutionRepositoryInterface executionRepository;

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Test
    void shouldReturnEmptyForNonExistingExecution() {
        var executionKilled = ExecutionKilledExecution.builder()
            .tenantId("tenant")
            .executionId("execution")
            .executionState(State.Type.FAILED)
            .build();

        var maybeExecutor = executionKilledExecutionMessageHandler.handle(executionKilled);

        assertTrue(maybeExecutor.isEmpty());
    }

    @Test
    void shouldReturnAnExecutorForExistingExecution() {
        var flow = Fixtures.flow();
        flowRepository.create(GenericFlow.of(flow));
        var execution = Execution.newExecution(flow, Collections.emptyList());
        executionRepository.save(execution);
        var executionKilledExecution = ExecutionKilledExecution.builder()
            .tenantId(execution.getTenantId())
            .executionId(execution.getId())
            .executionState(State.Type.KILLED)
            .build();

        var maybeExecutor = executionKilledExecutionMessageHandler.handle(executionKilledExecution);

        assertThat(maybeExecutor).isPresent();
        assertThat(maybeExecutor.get().getExecution().getState().getCurrent()).isEqualTo(State.Type.KILLED);
    }
}