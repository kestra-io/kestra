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
import io.kestra.core.models.executions.LoopExecutionEvent;
import io.kestra.core.models.executions.LoopRun;
import io.kestra.core.models.flows.State;
import io.kestra.executor.ExecutionStateStore;
import io.kestra.executor.ExecutorContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the kill-switch guards in {@link LoopExecutionEventMessageHandler}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoopExecutionEventMessageHandlerKillSwitchTest {

    @Mock KillSwitchService killSwitchService;
    @Mock ExecutionStateStore executionStateStore;

    LoopExecutionEventMessageHandler handler;
    LoopExecutionEvent message;
    LoopRun loopRun;
    Execution parentExecution;

    @BeforeEach
    void setUp() throws Exception {
        handler = new LoopExecutionEventMessageHandler();
        setField("killSwitchService", killSwitchService);
        setField("executionStateStore", executionStateStore);

        parentExecution = mockExecution("parent-exec-1");
        loopRun = mock(LoopRun.class);
        when(loopRun.parent()).thenReturn(parentExecution);

        message = mock(LoopExecutionEvent.class);
        when(message.executionId()).thenReturn("sub-exec-1");
        when(message.loopRun()).thenReturn(loopRun);
        when(message.state()).thenReturn(State.Type.SUCCESS);
    }

    @Test
    void shouldDelegateToLockWhenBothKillSwitchesPass() {
        // Given
        when(killSwitchService.evaluate("sub-exec-1")).thenReturn(EvaluationType.PASS);
        when(killSwitchService.evaluate("parent-exec-1")).thenReturn(EvaluationType.PASS);
        when(executionStateStore.lock(any(), any())).thenReturn(Optional.empty());

        // When
        handler.handle(message);

        // Then — both guards passed, lock was called
        verify(executionStateStore).lock(any(), any());
    }

    @Test
    void shouldReturnEmptyWhenSubExecutionKillSwitched() {
        // Given
        when(killSwitchService.evaluate("sub-exec-1")).thenReturn(EvaluationType.IGNORE);

        // When
        Optional<ExecutorContext> result = handler.handle(message);

        // Then
        assertThat(result).isEmpty();
        verify(executionStateStore, never()).lock(any(), any());
    }

    @Test
    void shouldReturnEmptyWhenParentExecutionKillSwitched() {
        // Given — sub-execution passes but parent is ignored
        when(killSwitchService.evaluate("sub-exec-1")).thenReturn(EvaluationType.PASS);
        when(killSwitchService.evaluate("parent-exec-1")).thenReturn(EvaluationType.IGNORE);

        // When
        Optional<ExecutorContext> result = handler.handle(message);

        // Then
        assertThat(result).isEmpty();
        verify(executionStateStore, never()).lock(any(), any());
    }

    private void setField(String fieldName, Object value) throws Exception {
        var field = LoopExecutionEventMessageHandler.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(handler, value);
    }

    private Execution mockExecution(String execId) {
        var exec = mock(Execution.class);
        when(exec.getId()).thenReturn(execId);
        return exec;
    }
}
