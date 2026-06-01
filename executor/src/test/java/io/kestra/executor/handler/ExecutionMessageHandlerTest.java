package io.kestra.executor.handler;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import io.kestra.core.killswitch.EvaluationType;
import io.kestra.core.killswitch.KillSwitchService;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.ExecutionEvent;
import io.kestra.executor.ExecutionStateStore;
import io.kestra.executor.ExecutorContext;
import io.kestra.executor.KillSwitchActionService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExecutionMessageHandlerTest {

    @Mock ExecutionStateStore executionStateStore;
    @Mock KillSwitchService killSwitchService;
    @Mock KillSwitchActionService killSwitchActionService;
    @Mock ExecutionEventMessageHandler executionEventMessageHandler;

    ExecutionMessageHandler handler;
    Execution execution;

    @BeforeEach
    void setUp() {
        handler = new ExecutionMessageHandler(
            executionStateStore,
            killSwitchService,
            killSwitchActionService,
            executionEventMessageHandler
        );
        execution = mockExecution("exec-1", "tenant", State.Type.CREATED);
    }

    @Test
    void shouldCreateAndProcessWhenPassThrough() {
        // Given
        var context = mock(ExecutorContext.class);
        when(killSwitchService.evaluate(execution)).thenReturn(EvaluationType.PASS);
        when(executionEventMessageHandler.handle(any(ExecutionEvent.class))).thenReturn(Optional.of(context));

        // When
        Optional<ExecutorContext> result = handler.handle(execution);

        // Then
        assertThat(result).contains(context);
        verify(executionStateStore).create(execution);
        verify(executionEventMessageHandler).handle(any(ExecutionEvent.class));
    }

    @Test
    void shouldAlwaysCreateExecutionBeforeKillSwitchCheck() {
        // Verify execution is persisted even when kill switch fires.
        // Given
        when(killSwitchService.evaluate(execution)).thenReturn(EvaluationType.IGNORE);

        // When
        handler.handle(execution);

        // Then — create called before kill switch check
        verify(executionStateStore).create(execution);
    }

    @Test
    void shouldReturnEmptyAndCallHandleWhenKillSwitched() {
        // Given — execution is NOT already KILLING/KILLED so isKillSwitched returns true for IGNORE
        var state = mock(State.class);
        when(state.getCurrent()).thenReturn(State.Type.CREATED);
        when(execution.getState()).thenReturn(state);
        when(killSwitchService.evaluate(execution)).thenReturn(EvaluationType.IGNORE);

        // When
        Optional<ExecutorContext> result = handler.handle(execution);

        // Then
        assertThat(result).isEmpty();
        verify(killSwitchActionService).handle(EvaluationType.IGNORE, "tenant", "exec-1");
        verify(executionEventMessageHandler, never()).handle(any());
    }

    @Test
    void shouldContinueProcessingWhenCreateThrows() {
        // Create failure is logged and processing continues.
        // Given
        var context = mock(ExecutorContext.class);
        doThrow(new RuntimeException("DB error")).when(executionStateStore).create(execution);
        when(killSwitchService.evaluate(execution)).thenReturn(EvaluationType.PASS);
        when(executionEventMessageHandler.handle(any(ExecutionEvent.class))).thenReturn(Optional.of(context));

        // When — must not throw
        Optional<ExecutorContext> result = handler.handle(execution);

        // Then — processing continues after create failure
        assertThat(result).contains(context);
        verify(executionEventMessageHandler).handle(any(ExecutionEvent.class));
    }

    @Test
    void shouldUseCreatedEventTypeWhenExecutionIsCreated() {
        // Given
        when(killSwitchService.evaluate(execution)).thenReturn(EvaluationType.PASS);
        when(executionEventMessageHandler.handle(any())).thenReturn(Optional.empty());

        // When
        handler.handle(execution);

        // Then
        verify(executionEventMessageHandler).handle(any(ExecutionEvent.class));
    }

    @Test
    void shouldUseUpdatedEventTypeWhenExecutionIsNotCreated() {
        // Given
        Execution updatedExecution = mockExecution("exec-2", "tenant", State.Type.RUNNING);
        when(killSwitchService.evaluate(updatedExecution)).thenReturn(EvaluationType.PASS);
        when(executionEventMessageHandler.handle(any())).thenReturn(Optional.empty());

        // When
        handler.handle(updatedExecution);

        // Then
        verify(executionEventMessageHandler).handle(any(ExecutionEvent.class));
    }

    private Execution mockExecution(String execId, String tenantId, State.Type stateType) {
        var state = mock(State.class);
        when(state.isCreated()).thenReturn(stateType == State.Type.CREATED);
        when(state.getCurrent()).thenReturn(stateType);
        var exec = mock(Execution.class);
        when(exec.getId()).thenReturn(execId);
        when(exec.getTenantId()).thenReturn(tenantId);
        when(exec.getState()).thenReturn(state);
        return exec;
    }
}
