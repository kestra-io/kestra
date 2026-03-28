package io.kestra.executor.handler;

import java.time.Instant;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.runners.ExecutionEvent;
import io.kestra.core.runners.ExecutionEventType;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class ExecutionEventMessageHandlerTest {
    @Inject
    private ExecutionEventMessageHandler executionEventMessageHandler;

    @Inject
    private ExecutionRepositoryInterface executionRepository;

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Test
    void shouldReturnEmptyForNonExistingExecution() {
        var executionEvent = new ExecutionEvent("tenant", "namespace", "flow", "execution", Instant.now(), ExecutionEventType.CREATED);

        var maybeExecutor = executionEventMessageHandler.handle(executionEvent);

        assertThat(maybeExecutor).isEmpty();
    }

    @Test
    void shouldReturnAnExecutorForExistingExecution() {
        var flow = flowRepository.create(GenericFlow.of(Fixtures.flow()));
        var execution = Execution.newExecution(flow, Collections.emptyList());
        executionRepository.save(execution);
        var executionEvent = new ExecutionEvent(execution, ExecutionEventType.CREATED);

        var maybeExecutor = executionEventMessageHandler.handle(executionEvent);

        assertThat(maybeExecutor).isPresent();
        assertThat(maybeExecutor.get().getExecution().getState().getCurrent()).isEqualTo(State.Type.RUNNING);
        assertThat(maybeExecutor.get().getExecution().getTaskRunList()).hasSize(1);
    }
}