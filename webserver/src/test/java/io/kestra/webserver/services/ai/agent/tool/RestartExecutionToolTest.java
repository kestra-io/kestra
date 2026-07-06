package io.kestra.webserver.services.ai.agent.tool;

import java.util.List;
import java.util.Optional;

import io.kestra.core.executor.command.ExecutionCommand;
import io.kestra.core.executor.command.Restart;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.webserver.services.ai.agent.AgentCallContext;
import io.kestra.webserver.services.ai.agent.domain.ToolFamily;
import io.kestra.webserver.services.ai.agent.domain.WritePolicy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RestartExecutionToolTest {
    private static final String TENANT = "main";

    private ExecutionRepositoryInterface executionRepository;
    private DispatchQueueInterface<ExecutionCommand> executionCommandQueue;
    private RestartExecutionTool tool;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        executionRepository = mock(ExecutionRepositoryInterface.class);
        executionCommandQueue = mock(DispatchQueueInterface.class);
        tool = new RestartExecutionTool(executionRepository, executionCommandQueue);
        AgentCallContext.set(TENANT);
    }

    @AfterEach
    void tearDown() {
        AgentCallContext.clear();
    }

    private static Execution executionWith(final State.Type state) {
        return Execution.builder()
            .id("exec-1")
            .tenantId(TENANT)
            .namespace("io.kestra.test")
            .flowId("flow-1")
            .state(State.of(state, List.of()))
            .build();
    }

    @Test
    void shouldExposeActConfirmMetadata() {
        // When / Then
        assertThat(tool.family()).isEqualTo(ToolFamily.ACT);
        assertThat(tool.writePolicy()).isEqualTo(WritePolicy.CONFIRM);
        assertThat(tool.permission()).isEqualTo("execution:restart");
    }

    @Test
    void shouldEmitRestartCommandWhenExecutionIsRestartable() throws Exception {
        // Given — a terminated (FAILED) execution can be restarted
        when(executionRepository.findById(TENANT, "exec-1")).thenReturn(Optional.of(executionWith(State.Type.FAILED)));

        // When
        String result = tool.restartExecution("exec-1", null);

        // Then — a Restart command for this execution is enqueued, carrying an operationId
        ArgumentCaptor<ExecutionCommand> captor = ArgumentCaptor.forClass(ExecutionCommand.class);
        verify(executionCommandQueue).emit(captor.capture());
        assertThat(captor.getValue()).isInstanceOfSatisfying(Restart.class, restart -> {
            assertThat(restart.executionId()).isEqualTo("exec-1");
            assertThat(restart.operationId()).isNotNull();
        });
        assertThat(result).contains("Restart requested for execution 'exec-1'");
    }

    @Test
    void shouldThrowWhenExecutionNotFound() {
        // Given
        when(executionRepository.findById(TENANT, "missing")).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> tool.restartExecution("missing", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Execution not found");
        verifyNoInteractions(executionCommandQueue);
    }

    @Test
    void shouldThrowWhenStateIsNotRestartable() {
        // Given — a RUNNING execution is neither terminated nor paused
        when(executionRepository.findById(TENANT, "exec-1")).thenReturn(Optional.of(executionWith(State.Type.RUNNING)));

        // When / Then
        assertThatThrownBy(() -> tool.restartExecution("exec-1", null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("cannot be restarted");
        verifyNoInteractions(executionCommandQueue);
    }

    @Test
    void shouldWrapQueueFailureInIllegalState() throws Exception {
        // Given
        when(executionRepository.findById(TENANT, "exec-1")).thenReturn(Optional.of(executionWith(State.Type.FAILED)));
        doThrow(new QueueException("boom")).when(executionCommandQueue).emit(any(ExecutionCommand.class));

        // When / Then
        assertThatThrownBy(() -> tool.restartExecution("exec-1", null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Failed to enqueue restart");
    }
}
