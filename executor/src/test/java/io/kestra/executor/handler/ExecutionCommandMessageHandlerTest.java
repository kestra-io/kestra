package io.kestra.executor.handler;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import io.kestra.core.async.AsyncOperationProcessedEvent.Outcome;
import io.kestra.core.async.AsyncOperationService;
import io.kestra.core.executor.command.Create;
import io.kestra.core.executor.command.ExecutionCommand;
import io.kestra.core.executor.command.Replay;
import io.kestra.core.killswitch.EvaluationType;
import io.kestra.core.killswitch.KillSwitchService;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionId;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.FlowMetaStoreInterface;
import io.kestra.core.services.ExecutionService;
import io.kestra.core.services.TaskOutputService;
import io.kestra.executor.ExecutionStateStore;
import io.kestra.executor.ExecutorContext;
import io.kestra.executor.KillSwitchActionService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExecutionCommandMessageHandlerTest {

    @Mock FlowMetaStoreInterface flowMetaStore;
    @Mock ExecutionService executionService;
    @Mock ExecutionStateStore executionStateStore;
    @Mock ExecutionEventMessageHandler executionEventMessageHandler;
    @Mock AsyncOperationService asyncOperationService;
    @Mock TaskOutputService taskOutputService;
    @Mock KillSwitchService killSwitchService;
    @Mock KillSwitchActionService killSwitchActionService;

    ExecutionCommandMessageHandler handler;
    Create createCommand;
    Execution sourceExecution;
    Replay replayCommand;

    @BeforeEach
    void setUp() {
        handler = new ExecutionCommandMessageHandler(
            executionService,
            executionStateStore,
            flowMetaStore,
            taskOutputService,
            asyncOperationService,
            executionEventMessageHandler,
            killSwitchService,
            killSwitchActionService
        );
        createCommand = Create.of(new ExecutionId("tenant", "ns", "flow-id", "exec-1", null))
            .withOperationId("op-1");
        sourceExecution = mockExecution("source-exec-id", "tenant", "ns", "flow-id");
        replayCommand = Replay.from(sourceExecution, "new-exec-id", null, null, null)
            .withOperationId("op-2");
    }

    @Test
    void shouldEmitSucceededOutcomeOnHappyPath() {
        // Given
        var flow = mock(FlowInterface.class);
        var execution = executionWithState(State.Type.CREATED);
        var context = mock(ExecutorContext.class);
        when(flowMetaStore.findById(any(), any(), any(), any())).thenReturn(Optional.of(flow));
        when(executionService.create(eq(createCommand), eq(flow))).thenReturn(execution);
        when(killSwitchService.evaluate(execution)).thenReturn(EvaluationType.PASS);
        when(executionEventMessageHandler.handle(any())).thenReturn(Optional.of(context));

        // When
        Optional<ExecutorContext> result = handler.handle(createCommand);

        // Then
        assertThat(result).contains(context);
        verify(asyncOperationService).emitProcessedIfAsync(createCommand, "tenant", "exec-1", Outcome.SUCCEEDED, null);
    }

    @Test
    void shouldEmitFailedOutcomeWhenFlowNotFound() {
        // Bug #1: FlowNotFoundException previously escaped the try/finally, so emitProcessedIfAsync
        // was never called and the controller would time out with a 504 instead of a clean error.
        when(flowMetaStore.findById(any(), any(), any(), any())).thenReturn(Optional.empty());

        // When — must not throw
        assertThatCode(() -> handler.handle(createCommand)).doesNotThrowAnyException();

        // Then — FAILED outcome must be signalled so the controller gets a 409, not a 504
        verify(asyncOperationService).emitProcessedIfAsync(eq(createCommand), eq("tenant"), eq("exec-1"), eq(Outcome.FAILED), any());
    }

    @Test
    void shouldEmitFailedOutcomeWhenStateStoreCreateFails() {
        // Bug #2: executionStateStore.create() failure was swallowed (only logged) and the handler
        // continued to emit SUCCEEDED — the controller returned 200 for an execution never persisted.
        var flow = mock(FlowInterface.class);
        var execution = mock(Execution.class); // state stubs not needed — exception fires before getState()
        when(flowMetaStore.findById(any(), any(), any(), any())).thenReturn(Optional.of(flow));
        when(executionService.create(eq(createCommand), eq(flow))).thenReturn(execution);
        doThrow(new RuntimeException("DB unavailable")).when(executionStateStore).create(execution);

        // When — must not throw
        assertThatCode(() -> handler.handle(createCommand)).doesNotThrowAnyException();

        // Then — FAILED outcome, not SUCCEEDED
        verify(asyncOperationService).emitProcessedIfAsync(eq(createCommand), eq("tenant"), eq("exec-1"), eq(Outcome.FAILED), any());
    }

    @Test
    void shouldEmitFailedOutcomeWhenEventHandlerFails() {
        var flow = mock(FlowInterface.class);
        var execution = executionWithState(State.Type.CREATED);
        when(flowMetaStore.findById(any(), any(), any(), any())).thenReturn(Optional.of(flow));
        when(executionService.create(eq(createCommand), eq(flow))).thenReturn(execution);
        when(killSwitchService.evaluate(execution)).thenReturn(EvaluationType.PASS);
        when(executionEventMessageHandler.handle(any())).thenThrow(new RuntimeException("handler error"));

        assertThatCode(() -> handler.handle(createCommand)).doesNotThrowAnyException();

        verify(asyncOperationService).emitProcessedIfAsync(eq(createCommand), eq("tenant"), eq("exec-1"), eq(Outcome.FAILED), any());
    }

    @Test
    void shouldPersistExecutionAndReturnEmptyWhenKillSwitchActive() {
        // Given
        var flow = mock(FlowInterface.class);
        var execution = mock(Execution.class); // no state stubs needed — kill switch fires before getState()
        when(flowMetaStore.findById(any(), any(), any(), any())).thenReturn(Optional.of(flow));
        when(executionService.create(eq(createCommand), eq(flow))).thenReturn(execution);
        when(killSwitchService.evaluate(execution)).thenReturn(EvaluationType.IGNORE);

        // When
        Optional<ExecutorContext> result = handler.handle(createCommand);

        // Then — execution was persisted but not processed further
        assertThat(result).isEmpty();
        verify(executionStateStore).create(execution);
        verify(executionEventMessageHandler, never()).handle(any());
        verify(asyncOperationService).emitProcessedIfAsync(createCommand, "tenant", "exec-1", Outcome.SUCCEEDED, null);
    }

    // ---- Existing-execution kill switch pre-check tests ----

    @Test
    void shouldReturnEmptyAndLogWhenKillSwitchIsIgnoreForExistingExecution() {
        // Given — a non-Create/Replay command targeting an existing execution that is IGNORED
        var command = mock(ExecutionCommand.class);
        when(command.executionId()).thenReturn("exec-1");
        var execution = mockExecution("exec-1", "tenant", "ns", "flow-id");
        when(killSwitchService.evaluate(command)).thenReturn(EvaluationType.IGNORE);
        when(executionStateStore.findById("exec-1")).thenReturn(execution);

        // When
        Optional<ExecutorContext> result = handler.handle(command);

        // Then — dropped without locking
        assertThat(result).isEmpty();
        verify(executionStateStore, never()).lock(any(), any());
    }

    @Test
    void shouldKillExecutionWhenKillSwitchIsKillForExistingExecution() {
        // Given
        var command = mock(ExecutionCommand.class);
        when(command.executionId()).thenReturn("exec-1");
        var execution = mockExecution("exec-1", "tenant", "ns", "flow-id");
        when(execution.getState().getCurrent()).thenReturn(State.Type.RUNNING);
        when(killSwitchService.evaluate(command)).thenReturn(EvaluationType.KILL);
        when(executionStateStore.findById("exec-1")).thenReturn(execution);

        // When
        Optional<ExecutorContext> result = handler.handle(command);

        // Then — delegated to KillSwitchActionService, not processed further
        assertThat(result).isEmpty();
        verify(killSwitchActionService).handle(EvaluationType.KILL, "tenant", "exec-1");
    }

    @Test
    void shouldCancelExecutionWhenKillSwitchIsCancelForExistingExecution() {
        // Given
        var command = mock(ExecutionCommand.class);
        when(command.executionId()).thenReturn("exec-1");
        var execution = mockExecution("exec-1", "tenant", "ns", "flow-id");
        when(execution.getState().getCurrent()).thenReturn(State.Type.RUNNING);
        when(killSwitchService.evaluate(command)).thenReturn(EvaluationType.CANCEL);
        when(executionStateStore.findById("exec-1")).thenReturn(execution);

        // When
        Optional<ExecutorContext> result = handler.handle(command);

        // Then — delegated to KillSwitchActionService, not processed further
        assertThat(result).isEmpty();
        verify(killSwitchActionService).handle(EvaluationType.CANCEL, "tenant", "exec-1");
    }

    // ---- Replay command tests ----

    @Test
    void replayShouldEmitSucceededOutcomeOnHappyPath() throws Exception {
        // Given
        var flow = mock(FlowWithSource.class);
        var newExecution = mockExecution("new-exec-id", "tenant", "ns", "flow-id");
        var context = mock(ExecutorContext.class);
        when(executionStateStore.findById("source-exec-id")).thenReturn(sourceExecution);
        when(flowMetaStore.findByExecutionThenInjectDefaults(any())).thenReturn(Optional.of(flow));
        when(executionService.replay(any(), eq(flow), isNull(), isNull(), any(), eq(true), eq("new-exec-id")))
            .thenReturn(newExecution);
        when(executionEventMessageHandler.handle(any())).thenReturn(Optional.of(context));

        // When
        Optional<ExecutorContext> result = handler.handle(replayCommand);

        // Then
        assertThat(result).contains(context);
        verify(asyncOperationService).emitProcessedIfAsync(replayCommand, "tenant", "new-exec-id", Outcome.SUCCEEDED, null);
    }

    @Test
    void replayShouldEmitFailedOutcomeWhenSourceExecutionNotFound() {
        // Given — source execution does not exist
        when(executionStateStore.findById("source-exec-id")).thenReturn(null);

        // When — must not throw
        assertThatCode(() -> handler.handle(replayCommand)).doesNotThrowAnyException();

        // Then — FAILED outcome, not SUCCEEDED
        verify(asyncOperationService).emitProcessedIfAsync(eq(replayCommand), eq("tenant"), eq("new-exec-id"), eq(Outcome.FAILED), any());
    }

    @Test
    void replayShouldEmitFailedOutcomeWhenFlowNotFound() {
        // Given
        when(executionStateStore.findById("source-exec-id")).thenReturn(sourceExecution);
        when(flowMetaStore.findByExecutionThenInjectDefaults(any())).thenReturn(Optional.empty());

        // When — must not throw
        assertThatCode(() -> handler.handle(replayCommand)).doesNotThrowAnyException();

        // Then
        verify(asyncOperationService).emitProcessedIfAsync(eq(replayCommand), eq("tenant"), eq("new-exec-id"), eq(Outcome.FAILED), any());
    }

    @Test
    void replayShouldEmitFailedOutcomeWhenStateStoreCreateFails() throws Exception {
        // Given
        var flow = mock(FlowWithSource.class);
        var newExecution = mockExecution("new-exec-id", "tenant", "ns", "flow-id");
        when(executionStateStore.findById("source-exec-id")).thenReturn(sourceExecution);
        when(flowMetaStore.findByExecutionThenInjectDefaults(any())).thenReturn(Optional.of(flow));
        when(executionService.replay(any(), any(), any(), any(), any(), eq(true), eq("new-exec-id")))
            .thenReturn(newExecution);
        doThrow(new RuntimeException("DB unavailable")).when(executionStateStore).create(newExecution);

        // When — must not throw
        assertThatCode(() -> handler.handle(replayCommand)).doesNotThrowAnyException();

        // Then — FAILED outcome, not SUCCEEDED
        verify(asyncOperationService).emitProcessedIfAsync(eq(replayCommand), eq("tenant"), eq("new-exec-id"), eq(Outcome.FAILED), any());
    }

    @Test
    void replayShouldPersistKilledExecutionAndReturnEmptyWhenKillSwitchIsKill() throws Exception {
        // Given
        var flow = mock(FlowWithSource.class);
        var newExecution = mockExecution("new-exec-id", "tenant", "ns", "flow-id");
        when(executionStateStore.findById("source-exec-id")).thenReturn(sourceExecution);
        when(flowMetaStore.findByExecutionThenInjectDefaults(any())).thenReturn(Optional.of(flow));
        when(executionService.replay(any(), eq(flow), isNull(), isNull(), any(), eq(true), eq("new-exec-id")))
            .thenReturn(newExecution);
        when(killSwitchService.evaluate(newExecution)).thenReturn(EvaluationType.KILL);
        when(newExecution.withState(State.Type.KILLED)).thenReturn(newExecution);
        when(newExecution.addLabel(any())).thenReturn(newExecution);

        // When
        Optional<ExecutorContext> result = handler.handle(replayCommand);

        // Then — persisted in KILLED state, no further processing
        assertThat(result).isEmpty();
        verify(executionStateStore).create(newExecution);
        verify(executionEventMessageHandler, never()).handle(any());
        verify(asyncOperationService).emitProcessedIfAsync(replayCommand, "tenant", "new-exec-id", Outcome.SUCCEEDED, null);
    }

    @Test
    void replayShouldPersistIgnoredExecutionAndReturnEmptyWhenKillSwitchIsIgnore() throws Exception {
        // Given
        var flow = mock(FlowWithSource.class);
        var newExecution = mockExecution("new-exec-id", "tenant", "ns", "flow-id");
        when(executionStateStore.findById("source-exec-id")).thenReturn(sourceExecution);
        when(flowMetaStore.findByExecutionThenInjectDefaults(any())).thenReturn(Optional.of(flow));
        when(executionService.replay(any(), eq(flow), isNull(), isNull(), any(), eq(true), eq("new-exec-id")))
            .thenReturn(newExecution);
        when(killSwitchService.evaluate(newExecution)).thenReturn(EvaluationType.IGNORE);

        // When
        Optional<ExecutorContext> result = handler.handle(replayCommand);

        // Then — persisted as-is, no further processing
        assertThat(result).isEmpty();
        verify(executionStateStore).create(newExecution);
        verify(executionEventMessageHandler, never()).handle(any());
        verify(asyncOperationService).emitProcessedIfAsync(replayCommand, "tenant", "new-exec-id", Outcome.SUCCEEDED, null);
    }

    @Test
    void replayShouldApplyRevisionWhenSpecified() throws Exception {
        // Given
        var commandWithRevision = replayCommand.withRevision(3);
        var flow = mock(Flow.class);
        var newExecution = mockExecution("new-exec-id", "tenant", "ns", "flow-id");
        var context = mock(ExecutorContext.class);
        when(executionStateStore.findById("source-exec-id")).thenReturn(sourceExecution);
        when(flowMetaStore.findById("tenant", "ns", "flow-id", Optional.of(3))).thenReturn(Optional.of(flow));
        when(executionService.replay(any(), eq(flow), isNull(), eq(3), any(), eq(true), eq("new-exec-id")))
            .thenReturn(newExecution);
        when(executionEventMessageHandler.handle(any())).thenReturn(Optional.of(context));

        // When
        Optional<ExecutorContext> result = handler.handle(commandWithRevision);

        // Then
        assertThat(result).contains(context);
        verify(flowMetaStore).findById("tenant", "ns", "flow-id", Optional.of(3));
    }

    // ---- helpers ----

    private Execution executionWithState(State.Type type) {
        var state = mock(State.class);
        when(state.isCreated()).thenReturn(type == State.Type.CREATED);
        var execution = mock(Execution.class);
        when(execution.getState()).thenReturn(state);
        return execution;
    }

    private Execution mockExecution(String execId, String tenantId, String namespace, String flowId) {
        var state = mock(State.class);
        when(state.isCreated()).thenReturn(false);
        var execution = mock(Execution.class);
        when(execution.getId()).thenReturn(execId);
        when(execution.getTenantId()).thenReturn(tenantId);
        when(execution.getNamespace()).thenReturn(namespace);
        when(execution.getFlowId()).thenReturn(flowId);
        when(execution.getState()).thenReturn(state);
        return execution;
    }
}
