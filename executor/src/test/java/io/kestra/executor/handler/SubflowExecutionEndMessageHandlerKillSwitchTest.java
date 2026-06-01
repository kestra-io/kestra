package io.kestra.executor.handler;

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
import io.kestra.core.runners.SubflowExecutionEnd;
import io.kestra.executor.ExecutionStateStore;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the kill-switch guards in {@link SubflowExecutionEndMessageHandler}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SubflowExecutionEndMessageHandlerKillSwitchTest {

    @Mock KillSwitchService killSwitchService;
    @Mock ExecutionStateStore executionStateStore;

    SubflowExecutionEndMessageHandler handler;
    SubflowExecutionEnd message;
    Execution childExecution;

    @BeforeEach
    void setUp() throws Exception {
        handler = new SubflowExecutionEndMessageHandler();
        setField("killSwitchService", killSwitchService);
        setField("executionStateStore", executionStateStore);

        childExecution = mockExecution("child-exec-1", State.Type.SUCCESS);
        message = new SubflowExecutionEnd(childExecution, "parent-exec-1", "taskrun-1", "task-1", State.Type.SUCCESS);
    }

    @Test
    void shouldDelegateToLockWhenBothKillSwitchesPass() {
        // Given
        when(killSwitchService.evaluate(childExecution)).thenReturn(EvaluationType.PASS);
        when(killSwitchService.evaluate("parent-exec-1")).thenReturn(EvaluationType.PASS);
        when(executionStateStore.lock(any(), any())).thenReturn(null);

        // When
        handler.handle(message);

        // Then — both guards passed, lock was called
        verify(executionStateStore).lock(any(), any());
    }

    @Test
    void shouldNotCallLockWhenChildExecutionKillSwitched() {
        // Given
        when(killSwitchService.evaluate(childExecution)).thenReturn(EvaluationType.IGNORE);

        // When
        handler.handle(message);

        // Then
        verify(executionStateStore, never()).lock(any(), any());
    }

    @Test
    void shouldNotCallLockWhenParentExecutionKillSwitched() {
        // Given — child passes but parent is ignored
        when(killSwitchService.evaluate(childExecution)).thenReturn(EvaluationType.PASS);
        when(killSwitchService.evaluate("parent-exec-1")).thenReturn(EvaluationType.IGNORE);

        // When
        handler.handle(message);

        // Then
        verify(executionStateStore, never()).lock(any(), any());
    }

    private void setField(String fieldName, Object value) throws Exception {
        var field = SubflowExecutionEndMessageHandler.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(handler, value);
    }

    private Execution mockExecution(String execId, State.Type stateType) {
        var state = mock(State.class);
        when(state.getCurrent()).thenReturn(stateType);
        var exec = mock(Execution.class);
        when(exec.getId()).thenReturn(execId);
        when(exec.getState()).thenReturn(state);
        return exec;
    }
}
