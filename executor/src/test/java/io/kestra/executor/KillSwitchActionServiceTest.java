package io.kestra.executor;

import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.kestra.core.killswitch.EvaluationType;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKilled;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.QueueException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KillSwitchActionServiceTest {

    @Mock ExecutionStateStore executionStateStore;
    @Mock BroadcastQueueInterface<ExecutionKilled> killQueue;

    KillSwitchActionService service;

    @BeforeEach
    void setUp() {
        service = new KillSwitchActionService(executionStateStore, killQueue);
    }

    @Test
    void shouldOnlyLogWhenIgnore() {
        // When
        assertThatCode(() -> service.handle(EvaluationType.IGNORE, "tenant", "exec-1")).doesNotThrowAnyException();

        // Then — no side effects
        verifyNoInteractions(executionStateStore, killQueue);
    }

    @Test
    void shouldTransitionToKillingAndEmitWhenKill() throws QueueException {
        // Given
        var execution = mockActiveExecution();
        when(execution.withState(State.Type.KILLING)).thenReturn(execution);
        when(execution.addLabel(any())).thenReturn(execution);
        invokeLockCallback("exec-1", execution);

        // When
        service.handle(EvaluationType.KILL, "tenant", "exec-1");

        // Then
        verify(executionStateStore).lock(eq("exec-1"), any());
        verify(execution).withState(State.Type.KILLING);
        verify(killQueue).emit(any(ExecutionKilled.class));
    }

    @Test
    void shouldStillEmitKillEventWhenExecutionAlreadyTerminated() throws QueueException {
        // Given — execution is already terminated; the lock callback returns null (no state transition)
        var execution = mockTerminatedExecution();
        invokeLockCallback("exec-1", execution);

        // When
        service.handle(EvaluationType.KILL, "tenant", "exec-1");

        // Then — state not mutated, but kill event must still be emitted to stop workers
        verify(executionStateStore).lock(eq("exec-1"), any());
        verify(execution, never()).withState(any());
        verify(killQueue).emit(any(ExecutionKilled.class));
    }

    @Test
    void shouldTransitionToCancelledWhenCancel() throws QueueException {
        // Given
        var execution = mockActiveExecution();
        when(execution.withState(State.Type.CANCELLED)).thenReturn(execution);
        when(execution.addLabel(any())).thenReturn(execution);
        invokeLockCallback("exec-1", execution);

        // When
        service.handle(EvaluationType.CANCEL, "tenant", "exec-1");

        // Then
        verify(executionStateStore).lock(eq("exec-1"), any());
        verify(execution).withState(State.Type.CANCELLED);
        verifyNoInteractions(killQueue);
    }

    @Test
    void shouldNotChangeStateWhenCancelAndExecutionAlreadyTerminated() {
        // Given
        var execution = mockTerminatedExecution();
        invokeLockCallback("exec-1", execution);

        // When
        service.handle(EvaluationType.CANCEL, "tenant", "exec-1");

        // Then — nothing to do; no state change, no queue interaction
        verify(executionStateStore).lock(eq("exec-1"), any());
        verify(execution, never()).withState(any());
        verifyNoInteractions(killQueue);
    }

    @Test
    void shouldLogErrorAndNotThrowWhenKillQueueEmitFails() throws QueueException {
        // Given
        var execution = mockActiveExecution();
        when(execution.withState(State.Type.KILLING)).thenReturn(execution);
        when(execution.addLabel(any())).thenReturn(execution);
        invokeLockCallback("exec-1", execution);
        doThrow(new QueueException("queue failure")).when(killQueue).emit(any(ExecutionKilled.class));

        // When — QueueException must be swallowed and logged, not propagated
        assertThatCode(() -> service.handle(EvaluationType.KILL, "tenant", "exec-1")).doesNotThrowAnyException();
    }

    // ---- helpers ----

    /** Stubs executionStateStore.lock() to synchronously invoke the callback with the given execution. */
    @SuppressWarnings("unchecked")
    private void invokeLockCallback(String executionId, Execution execution) {
        doAnswer(inv -> {
            Function<Execution, ExecutorContext> callback = inv.getArgument(1);
            callback.apply(execution);
            return null;
        }).when(executionStateStore).lock(eq(executionId), any());
    }

    private Execution mockActiveExecution() {
        var state = mock(State.class);
        when(state.isTerminated()).thenReturn(false);
        var execution = mock(Execution.class);
        when(execution.getState()).thenReturn(state);
        return execution;
    }

    private Execution mockTerminatedExecution() {
        var state = mock(State.class);
        when(state.isTerminated()).thenReturn(true);
        var execution = mock(Execution.class);
        when(execution.getState()).thenReturn(state);
        return execution;
    }
}
