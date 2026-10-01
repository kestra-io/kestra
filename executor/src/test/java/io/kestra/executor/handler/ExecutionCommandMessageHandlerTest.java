package io.kestra.executor.handler;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import io.kestra.core.async.AsyncOperationProcessedEvent.Outcome;
import io.kestra.core.async.AsyncOperationService;
import io.kestra.core.executor.command.ChangeTaskRunState;
import io.kestra.core.executor.command.Create;
import io.kestra.core.executor.command.ExecutionCommand;
import io.kestra.core.executor.command.ForceRun;
import io.kestra.core.executor.command.Pause;
import io.kestra.core.executor.command.Replay;
import io.kestra.core.executor.command.Restart;
import io.kestra.core.executor.command.Resume;
import io.kestra.core.executor.command.ResumeFromBreakpoint;
import io.kestra.core.executor.command.Unqueue;
import io.kestra.core.executor.command.UpdateLabels;
import io.kestra.core.executor.command.UpdateStatus;
import io.kestra.core.killswitch.EvaluationType;
import io.kestra.core.killswitch.KillSwitchService;
import io.kestra.core.models.Label;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionId;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.FlowMetaStoreInterface;
import io.kestra.core.runners.ProcessedFlow;
import io.kestra.core.services.ExecutionOutputService;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExecutionCommandMessageHandlerTest {

    @Mock
    FlowMetaStoreInterface flowMetaStore;
    @Mock
    ExecutionService executionService;
    @Mock
    ExecutionStateStore executionStateStore;
    @Mock
    ExecutionEventMessageHandler executionEventMessageHandler;
    @Mock
    AsyncOperationService asyncOperationService;
    @Mock
    TaskOutputService taskOutputService;
    @Mock
    ExecutionOutputService executionOutputService;
    @Mock
    KillSwitchService killSwitchService;
    @Mock
    KillSwitchActionService killSwitchActionService;

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
            executionOutputService,
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
        var flow = mock(FlowWithSource.class);
        var processedFlow = ProcessedFlow.of(flow);
        var execution = executionWithState(State.Type.CREATED);
        var context = mock(ExecutorContext.class);
        when(flowMetaStore.findByIdForRuntime(any(), any(), any(), any())).thenReturn(Optional.of(processedFlow));
        when(executionService.create(eq(createCommand), eq(processedFlow))).thenReturn(execution);
        when(killSwitchService.evaluate(execution)).thenReturn(EvaluationType.PASS);
        when(executionEventMessageHandler.handle(any())).thenReturn(Optional.of(context));

        // When
        Optional<ExecutorContext> result = handler.handle(createCommand);

        // Then
        assertThat(result).contains(context);
        verify(asyncOperationService).emitProcessedIfAsync(createCommand, "tenant", "exec-1", Outcome.SUCCEEDED, null);
        // the new execution snapshots the flow labels and variables, so it must never be built from the
        // raw flow — that dropped policy-injected labels on every non-triggered execution
        verify(flowMetaStore, never()).findById(any(), any(), any(), any());
    }

    @Test
    void shouldEmitFailedOutcomeWhenFlowNotFound() {
        // Bug #1: FlowNotFoundException previously escaped the try/finally, so emitProcessedIfAsync
        // was never called and the controller would time out with a 504 instead of a clean error.
        when(flowMetaStore.findByIdForRuntime(any(), any(), any(), any())).thenReturn(Optional.empty());

        // When — must not throw
        assertThatCode(() -> handler.handle(createCommand)).doesNotThrowAnyException();

        // Then — FAILED outcome must be signalled so the controller gets a 409, not a 504
        verify(asyncOperationService).emitProcessedIfAsync(eq(createCommand), eq("tenant"), eq("exec-1"), eq(Outcome.FAILED), any());
    }

    @Test
    void shouldEmitFailedOutcomeWhenStateStoreCreateFails() {
        // Bug #2: executionStateStore.create() failure was swallowed (only logged) and the handler
        // continued to emit SUCCEEDED — the controller returned 200 for an execution never persisted.
        var flow = mock(FlowWithSource.class);
        var processedFlow = ProcessedFlow.of(flow);
        var execution = mock(Execution.class); // state stubs not needed — exception fires before getState()
        when(flowMetaStore.findByIdForRuntime(any(), any(), any(), any())).thenReturn(Optional.of(processedFlow));
        when(executionService.create(eq(createCommand), eq(processedFlow))).thenReturn(execution);
        doThrow(new RuntimeException("DB unavailable")).when(executionStateStore).create(execution);

        // When — must not throw
        assertThatCode(() -> handler.handle(createCommand)).doesNotThrowAnyException();

        // Then — FAILED outcome, not SUCCEEDED
        verify(asyncOperationService).emitProcessedIfAsync(eq(createCommand), eq("tenant"), eq("exec-1"), eq(Outcome.FAILED), any());
    }

    @Test
    void shouldEmitFailedOutcomeWhenEventHandlerFails() {
        var flow = mock(FlowWithSource.class);
        var processedFlow = ProcessedFlow.of(flow);
        var execution = executionWithState(State.Type.CREATED);
        when(flowMetaStore.findByIdForRuntime(any(), any(), any(), any())).thenReturn(Optional.of(processedFlow));
        when(executionService.create(eq(createCommand), eq(processedFlow))).thenReturn(execution);
        when(killSwitchService.evaluate(execution)).thenReturn(EvaluationType.PASS);
        when(executionEventMessageHandler.handle(any())).thenThrow(new RuntimeException("handler error"));

        assertThatCode(() -> handler.handle(createCommand)).doesNotThrowAnyException();

        verify(asyncOperationService).emitProcessedIfAsync(eq(createCommand), eq("tenant"), eq("exec-1"), eq(Outcome.FAILED), any());
    }

    @Test
    void shouldReturnExecutorContextWhenKillSwitchIsKillForNewTriggeredExecution() {
        // Given
        var flow = mock(FlowWithSource.class);
        var processedFlow = ProcessedFlow.of(flow);
        var execution = mockExecution("exec-1", "tenant", "ns", "flow-id");
        when(execution.getState().isTerminated()).thenReturn(true);
        when(execution.getState().getCurrent()).thenReturn(State.Type.KILLED);
        when(execution.withState(State.Type.KILLED)).thenReturn(execution);
        when(execution.addLabel(any())).thenReturn(execution);
        when(flowMetaStore.findByIdForRuntime(any(), any(), any(), any())).thenReturn(Optional.of(processedFlow));
        when(executionService.create(eq(createCommand), eq(processedFlow))).thenReturn(execution);
        when(killSwitchService.evaluate(execution)).thenReturn(EvaluationType.KILL);

        // When
        Optional<ExecutorContext> result = handler.handle(createCommand);

        // Then — reaches the executor as a terminal ExecutorContext instead of being dropped
        assertThat(result).isPresent();
        assertThat(result.get().getExecution()).isEqualTo(execution);
        verify(executionStateStore).create(execution);
        verify(executionEventMessageHandler, never()).handle(any());
    }

    @Test
    void shouldReturnExecutorContextWhenKillSwitchIsCancelForNewTriggeredExecution() {
        // Given
        var flow = mock(FlowWithSource.class);
        var processedFlow = ProcessedFlow.of(flow);
        var execution = mockExecution("exec-1", "tenant", "ns", "flow-id");
        when(execution.getState().isTerminated()).thenReturn(true);
        when(execution.getState().getCurrent()).thenReturn(State.Type.CANCELLED);
        when(execution.withState(State.Type.CANCELLED)).thenReturn(execution);
        when(execution.addLabel(any())).thenReturn(execution);
        when(flowMetaStore.findByIdForRuntime(any(), any(), any(), any())).thenReturn(Optional.of(processedFlow));
        when(executionService.create(eq(createCommand), eq(processedFlow))).thenReturn(execution);
        when(killSwitchService.evaluate(execution)).thenReturn(EvaluationType.CANCEL);

        // When
        Optional<ExecutorContext> result = handler.handle(createCommand);

        // Then — reaches the executor as a terminal ExecutorContext instead of being dropped
        assertThat(result).isPresent();
        assertThat(result.get().getExecution()).isEqualTo(execution);
        verify(executionStateStore).create(execution);
        verify(executionEventMessageHandler, never()).handle(any());
    }

    @Test
    void shouldPersistCreatedAndReturnEmptyWhenKillSwitchIsIgnoreForNewExecution() {
        // Given — a Create whose execution the kill switch marks IGNORE (the CLI ignore-execution case,
        // by id / flow / namespace — the matching itself is covered by IgnoreExecutionServiceTest).
        var flow = mock(FlowWithSource.class);
        var processedFlow = ProcessedFlow.of(flow);
        var execution = mockExecution("exec-1", "tenant", "ns", "flow-id");
        when(execution.addLabel(any())).thenReturn(execution);
        when(flowMetaStore.findByIdForRuntime(any(), any(), any(), any())).thenReturn(Optional.of(processedFlow));
        when(executionService.create(eq(createCommand), eq(processedFlow))).thenReturn(execution);
        when(killSwitchService.evaluate(execution)).thenReturn(EvaluationType.IGNORE);

        // When
        Optional<ExecutorContext> result = handler.handle(createCommand);

        // Then — persisted as-is (tagged ignored) but never processed, so it stays in its CREATED state
        assertThat(result).isEmpty();
        verify(execution).addLabel(new Label(Label.KILL_SWITCH, "ignored"));
        verify(executionStateStore).create(execution);
        verify(executionEventMessageHandler, never()).handle(any());
        verify(asyncOperationService).emitProcessedIfAsync(createCommand, "tenant", "exec-1", Outcome.SUCCEEDED, null);
    }

    @Test
    void shouldReturnEmptyAndLogWhenKillSwitchIsIgnoreForExistingExecution() {
        // Given — a non-Create/Replay command targeting an existing execution that is IGNORED
        var command = mock(ExecutionCommand.class);
        when(command.executionId()).thenReturn("exec-1");
        var execution = mockExecution("exec-1", "tenant", "ns", "flow-id");
        when(killSwitchService.evaluate(command)).thenReturn(EvaluationType.IGNORE);
        when(executionStateStore.findByIdWithoutAcl("exec-1")).thenReturn(execution);

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
        when(executionStateStore.findByIdWithoutAcl("exec-1")).thenReturn(execution);

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
        when(executionStateStore.findByIdWithoutAcl("exec-1")).thenReturn(execution);

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
        when(executionStateStore.findByIdWithoutAcl("source-exec-id")).thenReturn(sourceExecution);
        when(flowMetaStore.findByExecutionForRuntime(any())).thenReturn(Optional.of(flow));
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
        when(executionStateStore.findByIdWithoutAcl("source-exec-id")).thenReturn(null);

        // When — must not throw
        assertThatCode(() -> handler.handle(replayCommand)).doesNotThrowAnyException();

        // Then — FAILED outcome, not SUCCEEDED
        verify(asyncOperationService).emitProcessedIfAsync(eq(replayCommand), eq("tenant"), eq("new-exec-id"), eq(Outcome.FAILED), any());
    }

    @Test
    void replayShouldEmitFailedOutcomeWhenFlowNotFound() {
        // Given
        when(executionStateStore.findByIdWithoutAcl("source-exec-id")).thenReturn(sourceExecution);
        when(flowMetaStore.findByExecutionForRuntime(any())).thenReturn(Optional.empty());

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
        when(executionStateStore.findByIdWithoutAcl("source-exec-id")).thenReturn(sourceExecution);
        when(flowMetaStore.findByExecutionForRuntime(any())).thenReturn(Optional.of(flow));
        when(executionService.replay(any(), any(), any(), any(), any(), eq(true), eq("new-exec-id")))
            .thenReturn(newExecution);
        doThrow(new RuntimeException("DB unavailable")).when(executionStateStore).create(newExecution);

        // When — must not throw
        assertThatCode(() -> handler.handle(replayCommand)).doesNotThrowAnyException();

        // Then — FAILED outcome, not SUCCEEDED
        verify(asyncOperationService).emitProcessedIfAsync(eq(replayCommand), eq("tenant"), eq("new-exec-id"), eq(Outcome.FAILED), any());
    }

    @Test
    void replayShouldReturnExecutorContextWhenKillSwitchIsKill() throws Exception {
        // Given
        var flow = mock(FlowWithSource.class);
        var newExecution = mockExecution("new-exec-id", "tenant", "ns", "flow-id");
        when(newExecution.getState().isTerminated()).thenReturn(true);
        when(newExecution.getState().getCurrent()).thenReturn(State.Type.KILLED);
        when(executionStateStore.findByIdWithoutAcl("source-exec-id")).thenReturn(sourceExecution);
        when(flowMetaStore.findByExecutionForRuntime(any())).thenReturn(Optional.of(flow));
        when(executionService.replay(any(), eq(flow), isNull(), isNull(), any(), eq(true), eq("new-exec-id")))
            .thenReturn(newExecution);
        when(killSwitchService.evaluate(newExecution)).thenReturn(EvaluationType.KILL);
        when(newExecution.withState(State.Type.KILLED)).thenReturn(newExecution);
        when(newExecution.addLabel(any())).thenReturn(newExecution);

        // When
        Optional<ExecutorContext> result = handler.handle(replayCommand);

        // Then — persisted in KILLED state and returned as a terminal ExecutorContext, not dropped
        assertThat(result).isPresent();
        assertThat(result.get().getExecution()).isEqualTo(newExecution);
        verify(executionStateStore).create(newExecution);
        verify(executionEventMessageHandler, never()).handle(any());
        verify(asyncOperationService).emitProcessedIfAsync(replayCommand, "tenant", "new-exec-id", Outcome.SUCCEEDED, null);
    }

    @Test
    void replayShouldPersistIgnoredExecutionAndReturnEmptyWhenKillSwitchIsIgnore() throws Exception {
        // Given
        var flow = mock(FlowWithSource.class);
        var newExecution = mockExecution("new-exec-id", "tenant", "ns", "flow-id");
        when(executionStateStore.findByIdWithoutAcl("source-exec-id")).thenReturn(sourceExecution);
        when(flowMetaStore.findByExecutionForRuntime(any())).thenReturn(Optional.of(flow));
        when(executionService.replay(any(), eq(flow), isNull(), isNull(), any(), eq(true), eq("new-exec-id")))
            .thenReturn(newExecution);
        when(killSwitchService.evaluate(newExecution)).thenReturn(EvaluationType.IGNORE);

        // When
        Optional<ExecutorContext> result = handler.handle(replayCommand);

        // Then — persisted as-is, no further processing
        assertThat(result).isEmpty();
        verify(executionStateStore).create(any());
        verify(executionEventMessageHandler, never()).handle(any());
        verify(asyncOperationService).emitProcessedIfAsync(replayCommand, "tenant", "new-exec-id", Outcome.SUCCEEDED, null);
    }

    @Test
    void replayShouldApplyRevisionWhenSpecified() throws Exception {
        // Given
        var commandWithRevision = replayCommand.withRevision(3);
        var flow = mock(FlowWithSource.class);
        var newExecution = mockExecution("new-exec-id", "tenant", "ns", "flow-id");
        var context = mock(ExecutorContext.class);
        when(executionStateStore.findByIdWithoutAcl("source-exec-id")).thenReturn(sourceExecution);
        when(flowMetaStore.findByIdForRuntime("tenant", "ns", "flow-id", Optional.of(3))).thenReturn(Optional.of(ProcessedFlow.of(flow)));
        when(executionService.replay(any(), eq(flow), isNull(), eq(3), any(), eq(true), eq("new-exec-id")))
            .thenReturn(newExecution);
        when(executionEventMessageHandler.handle(any())).thenReturn(Optional.of(context));

        // When
        Optional<ExecutorContext> result = handler.handle(commandWithRevision);

        // Then
        assertThat(result).contains(context);
        // the replayed execution snapshots the flow labels and variables, so the pinned revision must be
        // resolved as the executor will run it
        verify(flowMetaStore).findByIdForRuntime("tenant", "ns", "flow-id", Optional.of(3));
        verify(flowMetaStore, never()).findById(any(), any(), any(), any());
    }

    // ---- lock-path command dispatch ----

    @Test
    void shouldRouteRestartCommandToRestart() throws Exception {
        var existing = lockedExecution("exec-1", State.Type.FAILED);
        var updated = lockedExecution("exec-1", State.Type.RESTARTED);
        var flow = mock(FlowWithSource.class);
        stubLock(existing, flow);
        var command = Restart.from(existing, 3).withOperationId("op");
        when(executionService.restart(existing, flow, 3, true)).thenReturn(updated);

        Optional<ExecutorContext> result = handler.handle(command);

        assertThat(result).isPresent();
        assertThat(result.get().getExecution()).isEqualTo(updated);
        verify(executionService).restart(existing, flow, 3, true);
        verify(asyncOperationService).emitProcessedIfAsync(command, "tenant", "exec-1", Outcome.SUCCEEDED, null);
    }

    @Test
    void shouldRoutePauseCommandToPause() throws Exception {
        var existing = lockedExecution("exec-1", State.Type.RUNNING);
        var updated = lockedExecution("exec-1", State.Type.PAUSED);
        stubLock(existing, mock(FlowWithSource.class));
        var command = Pause.from(existing).withOperationId("op");
        when(executionService.pause(existing)).thenReturn(updated);

        Optional<ExecutorContext> result = handler.handle(command);

        assertThat(result).isPresent();
        assertThat(result.get().getExecution()).isEqualTo(updated);
        verify(executionService).pause(existing);
        verify(asyncOperationService).emitProcessedIfAsync(command, "tenant", "exec-1", Outcome.SUCCEEDED, null);
    }

    @Test
    void shouldRouteUnqueueCommandToUnqueueWithState() throws Exception {
        var existing = lockedExecution("exec-1", State.Type.QUEUED);
        var updated = lockedExecution("exec-1", State.Type.RUNNING);
        stubLock(existing, mock(FlowWithSource.class));
        var command = Unqueue.from(existing, State.Type.RUNNING).withOperationId("op");
        when(executionService.unqueue(existing, State.Type.RUNNING)).thenReturn(updated);

        Optional<ExecutorContext> result = handler.handle(command);

        assertThat(result).isPresent();
        assertThat(result.get().getExecution()).isEqualTo(updated);
        verify(executionService).unqueue(existing, State.Type.RUNNING);
    }

    @Test
    void shouldRouteForceRunCommandToForceRun() throws Exception {
        var existing = lockedExecution("exec-1", State.Type.QUEUED);
        var updated = lockedExecution("exec-1", State.Type.RUNNING);
        var flow = mock(FlowWithSource.class);
        stubLock(existing, flow);
        var command = ForceRun.from(existing).withOperationId("op");
        when(executionService.forceRun(existing, flow)).thenReturn(updated);

        Optional<ExecutorContext> result = handler.handle(command);

        assertThat(result).isPresent();
        assertThat(result.get().getExecution()).isEqualTo(updated);
        verify(executionService).forceRun(existing, flow);
    }

    @Test
    void shouldRouteChangeTaskRunStateCommandWithTaskRunIdAndState() throws Exception {
        var existing = lockedExecution("exec-1", State.Type.FAILED);
        var updated = lockedExecution("exec-1", State.Type.RESTARTED);
        var flow = mock(FlowWithSource.class);
        stubLock(existing, flow);
        var command = ChangeTaskRunState.from(existing, "taskrun-1", State.Type.SUCCESS).withOperationId("op");
        when(executionService.changeTaskRunState(existing, flow, "taskrun-1", State.Type.SUCCESS)).thenReturn(updated);

        Optional<ExecutorContext> result = handler.handle(command);

        assertThat(result).isPresent();
        assertThat(result.get().getExecution()).isEqualTo(updated);
        verify(executionService).changeTaskRunState(existing, flow, "taskrun-1", State.Type.SUCCESS);
    }

    @Test
    void shouldRouteUpdateLabelsCommandWithLabels() throws Exception {
        var existing = lockedExecution("exec-1", State.Type.RUNNING);
        var updated = lockedExecution("exec-1", State.Type.RUNNING);
        stubLock(existing, mock(FlowWithSource.class));
        var labels = List.of(new Label("team", "data"));
        var command = UpdateLabels.from(existing, labels).withOperationId("op");
        when(executionService.updateLabels(existing, labels)).thenReturn(updated);

        Optional<ExecutorContext> result = handler.handle(command);

        assertThat(result).isPresent();
        assertThat(result.get().getExecution()).isEqualTo(updated);
        verify(executionService).updateLabels(existing, labels);
    }

    @Test
    void shouldRouteUpdateStatusCommandToChangeState() throws Exception {
        var existing = lockedExecution("exec-1", State.Type.RUNNING);
        var updated = lockedExecution("exec-1", State.Type.SUCCESS);
        stubLock(existing, mock(FlowWithSource.class));
        var command = UpdateStatus.from(existing, State.Type.SUCCESS).withOperationId("op");
        when(executionService.changeState(existing, State.Type.SUCCESS)).thenReturn(updated);

        Optional<ExecutorContext> result = handler.handle(command);

        assertThat(result).isPresent();
        assertThat(result.get().getExecution()).isEqualTo(updated);
        verify(executionService).changeState(existing, State.Type.SUCCESS);
    }

    @Test
    void shouldRouteResumeFromBreakpointCommandWithBreakpoints() throws Exception {
        var existing = lockedExecution("exec-1", State.Type.BREAKPOINT);
        var updated = lockedExecution("exec-1", State.Type.RUNNING);
        stubLock(existing, mock(FlowWithSource.class));
        var breakpoints = Optional.of("task-a");
        var command = ResumeFromBreakpoint.from(existing, breakpoints).withOperationId("op");
        when(executionService.resumeFromBreakpoint(existing, breakpoints)).thenReturn(updated);

        Optional<ExecutorContext> result = handler.handle(command);

        assertThat(result).isPresent();
        assertThat(result.get().getExecution()).isEqualTo(updated);
        verify(executionService).resumeFromBreakpoint(existing, breakpoints);
    }

    @Test
    void shouldRouteResumeCommandToRunningWithInputsAndResumed() throws Exception {
        var existing = lockedExecution("exec-1", State.Type.PAUSED);
        var updated = lockedExecution("exec-1", State.Type.RUNNING);
        var flow = mock(FlowWithSource.class);
        stubLock(existing, flow);
        var resumed = io.kestra.plugin.core.flow.Pause.Resumed.now();
        Map<String, Object> inputs = Map.of("approved", true);
        var command = Resume.from(existing, resumed, inputs).withOperationId("op");
        when(executionService.resume(existing, flow, State.Type.RUNNING, inputs, resumed)).thenReturn(updated);

        Optional<ExecutorContext> result = handler.handle(command);

        assertThat(result).isPresent();
        assertThat(result.get().getExecution()).isEqualTo(updated);
        verify(executionService).resume(existing, flow, State.Type.RUNNING, inputs, resumed);
    }

    @Test
    void shouldEmitFailedOutcomeWhenServiceThrowsInLockPath() throws Exception {
        var existing = lockedExecution("exec-1", State.Type.RUNNING);
        stubLock(existing, mock(FlowWithSource.class));
        var command = Pause.from(existing).withOperationId("op");
        when(executionService.pause(existing)).thenThrow(new RuntimeException("boom"));

        Optional<ExecutorContext> result = handler.handle(command);

        assertThat(result).isEmpty();
        verify(asyncOperationService).emitProcessedIfAsync(eq(command), eq("tenant"), eq("exec-1"), eq(Outcome.FAILED), any());
    }

    @Test
    void shouldEmitFailedOutcomeWhenFlowNotFoundInLockPath() {
        var existing = lockedExecution("exec-1", State.Type.RUNNING);
        stubLock(existing, null);
        var command = Pause.from(existing).withOperationId("op");

        Optional<ExecutorContext> result = handler.handle(command);

        assertThat(result).isEmpty();
        verifyNoInteractions(executionService);
        verify(asyncOperationService).emitProcessedIfAsync(eq(command), eq("tenant"), eq("exec-1"), eq(Outcome.FAILED), any());
    }

    @Test
    void shouldYieldNullForInvalidCommand() {
        var existing = lockedExecution("exec-1", State.Type.RUNNING);
        stubLock(existing, mock(FlowWithSource.class));
        var command = new ExecutionCommand.Invalid("tenant", "ns", "flow-id", "exec-1", null, null);

        Optional<ExecutorContext> result = handler.handle(command);

        assertThat(result).isEmpty();
        verifyNoInteractions(executionService);
    }

    // ---- helpers ----

    private void stubLock(Execution existing, FlowWithSource flow) {
        when(killSwitchService.evaluate(any(ExecutionCommand.class))).thenReturn(EvaluationType.PASS);
        if (flow != null) {
            when(flowMetaStore.findByExecutionForRuntime(any())).thenReturn(Optional.of(flow));
        }
        when(executionStateStore.lock(any(), any())).thenAnswer(invocation -> {
            Function<Execution, ExecutorContext> function = invocation.getArgument(1);
            return Optional.ofNullable(function.apply(existing));
        });
    }

    private Execution lockedExecution(String execId, State.Type current) {
        var execution = mockExecution(execId, "tenant", "ns", "flow-id");
        when(execution.getState().getCurrent()).thenReturn(current);
        when(execution.getOutputs()).thenReturn(null);
        when(execution.getTaskRunList()).thenReturn(null);
        return execution;
    }

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
