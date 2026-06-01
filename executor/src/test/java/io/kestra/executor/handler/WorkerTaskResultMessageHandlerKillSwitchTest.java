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
import io.kestra.core.runners.WorkerTaskResult;
import io.kestra.executor.ExecutionStateStore;
import io.kestra.executor.ExecutorContext;
import io.kestra.executor.KillSwitchActionService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the kill-switch guard in {@link WorkerTaskResultMessageHandler}.
 *
 * <p>The guard follows the ECMH pattern:
 * evaluate(taskRun) → if not PASS → findById(executionId) → null-check → isKillSwitched → handle</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkerTaskResultMessageHandlerKillSwitchTest {

    @Mock KillSwitchService killSwitchService;
    @Mock KillSwitchActionService killSwitchActionService;
    @Mock ExecutionStateStore executionStateStore;

    WorkerTaskResultMessageHandler handler;
    WorkerTaskResult message;
    TaskRun taskRun;

    @BeforeEach
    void setUp() throws Exception {
        handler = new WorkerTaskResultMessageHandler();
        setField("killSwitchService", killSwitchService);
        setField("killSwitchActionService", killSwitchActionService);
        setField("executionStateStore", executionStateStore);

        taskRun = TaskRun.builder()
            .id("taskrun-1")
            .executionId("exec-1")
            .taskId("task-1")
            .build();
        message = WorkerTaskResult.builder().taskRun(taskRun).build();
    }

    @Test
    void shouldDelegateToLockWhenKillSwitchPasses() {
        // Given — lock returns empty (no execution found), fine for guard test
        when(killSwitchService.evaluate(taskRun)).thenReturn(EvaluationType.PASS);
        when(executionStateStore.lock(any(), any())).thenReturn(Optional.empty());

        // When
        handler.handle(message);

        // Then — guard did not intercept; lock was attempted
        verify(killSwitchActionService, never()).handle(any(), any(), any());
        verify(executionStateStore).lock(any(), any());
    }

    @Test
    void shouldReturnEmptyAndHandleWhenKillSwitched() {
        // Given
        var execution = mockExecution("exec-1", "tenant", State.Type.RUNNING);
        when(killSwitchService.evaluate(taskRun)).thenReturn(EvaluationType.IGNORE);
        when(executionStateStore.findById("exec-1")).thenReturn(execution);

        // When
        Optional<ExecutorContext> result = handler.handle(message);

        // Then
        assertThat(result).isEmpty();
        verify(killSwitchActionService).handle(EvaluationType.IGNORE, "tenant", "exec-1");
        verify(executionStateStore, never()).lock(any(), any());
    }

    @Test
    void shouldDelegateToLockWhenFindByIdReturnsNull() {
        // Guard must not NPE and must fall through to normal processing.
        // Given
        when(killSwitchService.evaluate(taskRun)).thenReturn(EvaluationType.IGNORE);
        when(executionStateStore.findById("exec-1")).thenReturn(null);
        when(executionStateStore.lock(any(), any())).thenReturn(Optional.empty());

        // When — must not throw
        handler.handle(message);

        // Then — null execution skips the kill action and falls through
        verify(killSwitchActionService, never()).handle(any(), any(), any());
        verify(executionStateStore).lock(any(), any());
    }

    @Test
    void shouldDelegateToLockWhenKillEvaluationButExecutionAlreadyKilling() {
        // For KILL type: isKillSwitched returns false when execution is already KILLING,
        // preventing a redundant kill loop.
        // Given
        var execution = mockExecution("exec-1", "tenant", State.Type.KILLING);
        when(killSwitchService.evaluate(taskRun)).thenReturn(EvaluationType.KILL);
        when(executionStateStore.findById("exec-1")).thenReturn(execution);
        when(executionStateStore.lock(any(), any())).thenReturn(Optional.empty());

        // When
        handler.handle(message);

        // Then
        verify(killSwitchActionService, never()).handle(any(), any(), any());
        verify(executionStateStore).lock(any(), any());
    }

    private void setField(String fieldName, Object value) throws Exception {
        var field = WorkerTaskResultMessageHandler.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(handler, value);
    }

    private Execution mockExecution(String execId, String tenantId, State.Type stateType) {
        var state = mock(State.class);
        when(state.getCurrent()).thenReturn(stateType);
        var exec = mock(Execution.class);
        when(exec.getId()).thenReturn(execId);
        when(exec.getTenantId()).thenReturn(tenantId);
        when(exec.getState()).thenReturn(state);
        return exec;
    }
}
