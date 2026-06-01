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
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.SubflowExecutionResult;
import io.kestra.executor.ExecutionStateStore;
import io.kestra.executor.ExecutorContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the kill-switch guards in {@link SubflowExecutionResultMessageHandler}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SubflowExecutionResultMessageHandlerKillSwitchTest {

    @Mock KillSwitchService killSwitchService;
    @Mock ExecutionStateStore executionStateStore;

    SubflowExecutionResultMessageHandler handler;
    SubflowExecutionResult message;
    TaskRun parentTaskRun;

    @BeforeEach
    void setUp() throws Exception {
        handler = new SubflowExecutionResultMessageHandler();
        setField("killSwitchService", killSwitchService);
        setField("executionStateStore", executionStateStore);

        parentTaskRun = TaskRun.builder()
            .id("taskrun-1")
            .executionId("parent-exec-1")
            .taskId("task-1")
            .state(new State().withState(State.Type.RUNNING))
            .build();
        message = SubflowExecutionResult.builder()
            .executionId("child-exec-1")
            .parentTaskRun(parentTaskRun)
            .build();
    }

    @Test
    void shouldDelegateToLockWhenBothKillSwitchesPass() {
        // Given
        when(killSwitchService.evaluate("child-exec-1")).thenReturn(EvaluationType.PASS);
        when(killSwitchService.evaluate(parentTaskRun)).thenReturn(EvaluationType.PASS);
        when(executionStateStore.lock(any(), any())).thenReturn(Optional.empty());

        // When
        handler.handle(message);

        // Then — both guards passed, lock was called
        verify(executionStateStore).lock(any(), any());
    }

    @Test
    void shouldReturnEmptyWhenChildExecutionKillSwitched() {
        // Given
        when(killSwitchService.evaluate("child-exec-1")).thenReturn(EvaluationType.IGNORE);

        // When
        Optional<ExecutorContext> result = handler.handle(message);

        // Then
        assertThat(result).isEmpty();
        verify(executionStateStore, never()).lock(any(), any());
    }

    @Test
    void shouldReturnEmptyWhenParentTaskRunKillSwitched() {
        // Given — child passes but parent is ignored
        when(killSwitchService.evaluate("child-exec-1")).thenReturn(EvaluationType.PASS);
        when(killSwitchService.evaluate(parentTaskRun)).thenReturn(EvaluationType.IGNORE);

        // When
        Optional<ExecutorContext> result = handler.handle(message);

        // Then
        assertThat(result).isEmpty();
        verify(executionStateStore, never()).lock(any(), any());
    }

    private void setField(String fieldName, Object value) throws Exception {
        var field = SubflowExecutionResultMessageHandler.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(handler, value);
    }
}
