package io.kestra.executor.handler;

import java.util.Optional;

import io.kestra.core.async.AsyncOperationProcessedEvent;
import io.kestra.core.async.AsyncOperationService;
import io.kestra.core.exceptions.FlowNotFoundException;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.executor.command.*;
import io.kestra.core.killswitch.EvaluationType;
import io.kestra.core.killswitch.KillSwitchService;
import io.kestra.core.models.Label;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.ExecutionEvent;
import io.kestra.executor.KillSwitchActionService;
import io.kestra.core.runners.ExecutionEventType;
import io.kestra.core.runners.FlowMetaStoreInterface;
import io.kestra.core.services.ExecutionService;
import io.kestra.core.services.TaskOutputService;
import io.kestra.core.utils.ListUtils;
import io.kestra.executor.ExecutionStateStore;
import io.kestra.executor.ExecutorContext;
import io.kestra.executor.ExecutorMessageHandler;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class ExecutionCommandMessageHandler implements ExecutorMessageHandler<ExecutionCommand> {
    private final ExecutionService executionService;
    private final ExecutionStateStore executionStateStore;
    private final FlowMetaStoreInterface flowMetaStore;
    private final TaskOutputService taskOutputService;
    private final AsyncOperationService asyncOperationService;
    private final ExecutionEventMessageHandler executionEventMessageHandler;
    private final KillSwitchService killSwitchService;
    private final KillSwitchActionService killSwitchActionService;

    @Inject
    public ExecutionCommandMessageHandler(
        ExecutionService executionService,
        ExecutionStateStore executionStateStore,
        FlowMetaStoreInterface flowMetaStore,
        TaskOutputService taskOutputService,
        AsyncOperationService asyncOperationService,
        ExecutionEventMessageHandler executionEventMessageHandler,
        KillSwitchService killSwitchService,
        KillSwitchActionService killSwitchActionService) {
        this.executionService = executionService;
        this.executionStateStore = executionStateStore;
        this.flowMetaStore = flowMetaStore;
        this.taskOutputService = taskOutputService;
        this.asyncOperationService = asyncOperationService;
        this.executionEventMessageHandler = executionEventMessageHandler;
        this.killSwitchService = killSwitchService;
        this.killSwitchActionService = killSwitchActionService;
    }

    @Override
    public Optional<ExecutorContext> handle(ExecutionCommand message) {
        // Create and Replay bootstrap a new execution — they cannot go through executionStateStore.lock()
        // because the execution doesn't exist yet. All other commands mutate an existing execution via lock.
        if (message instanceof Create createCommand) {
            return handleCreate(createCommand);
        }
        if (message instanceof Replay replayCommand) {
            return handleReplay(replayCommand);
        }

        // For existing executions, check kill switch before taking the lock.
        EvaluationType evaluationType = killSwitchService.evaluate(message);
        if (evaluationType != EvaluationType.PASS) {
            var execution = executionStateStore.findById(message.executionId());
            if (execution != null && evaluationType.isKillSwitched(execution)) {
                killSwitchActionService.handle(evaluationType, execution.getTenantId(), execution.getId());
                return Optional.empty();
            }
        }

        return executionStateStore.lock(message.executionId(), execution ->
        {
            AsyncOperationProcessedEvent.Outcome outcome = AsyncOperationProcessedEvent.Outcome.SUCCEEDED;
            String error = null;
            try {
                var flow = flowMetaStore.findByExecutionThenInjectDefaults(execution).orElseThrow(() -> new FlowNotFoundException(execution));
                var executorContext = new ExecutorContext(execution, flow);
                var newExecution = switch (message) {
                    case Restart restartCommand ->
                        executionService.restart(execution, executorContext.getFlow(), restartCommand.revision(), true);
                    case Pause ignored ->
                        executionService.pause(execution);
                    case Unqueue unqueueCommand ->
                        executionService.unqueue(execution, unqueueCommand.state());
                    case ForceRun ignored ->
                        executionService.forceRun(execution, executorContext.getFlow());
                    case ChangeTaskRunState changeTaskRunStateCommand ->
                        executionService.changeTaskRunState(execution, flow, changeTaskRunStateCommand.taskRunId(), changeTaskRunStateCommand.state());
                    case UpdateLabels updateLabels ->
                        executionService.updateLabels(execution, updateLabels.labels());
                    case UpdateStatus updateStatusCommand ->
                        executionService.changeState(execution, updateStatusCommand.state());
                    case ResumeFromBreakpoint resumeFromBreakpointCommand ->
                        executionService.resumeFromBreakpoint(execution, resumeFromBreakpointCommand.breakpoints());
                    case Resume resumeCommand ->
                        executionService.resume(execution, flow, State.Type.RUNNING, resumeCommand.resumeInputs(), resumeCommand.resumed());
                    case ExecutionCommand.Invalid ignored -> {
                        log.error("Invalid command for execution {}: ignoring command with eventId {}", message.executionId(), message.eventId());
                        yield null;
                    }
                    default -> throw new IllegalStateException("Unexpected value: " + message); // should never happen, would be a bug
                };
                return newExecution != null ? executorContext.withExecution(migrateTaskOutputs(newExecution), "ExecutionCommandMessageHandler") : null;
            } catch (Exception e) {
                log.error("Unable to process event for execution {}: ignoring {} command with eventId {}", message.executionId(), message.getClass().getSimpleName(), message.eventId(), e);
                outcome = AsyncOperationProcessedEvent.Outcome.FAILED;
                error = e.getMessage();
                return null;
            } finally {
                asyncOperationService.emitProcessedIfAsync(message, message.tenantId(), message.executionId(), outcome, error);
            }
        });
    }

    private Optional<ExecutorContext> handleCreate(Create command) {
        AsyncOperationProcessedEvent.Outcome outcome = AsyncOperationProcessedEvent.Outcome.SUCCEEDED;
        String error = null;
        try {
            var flow = flowMetaStore
                .findById(command.tenantId(), command.namespace(), command.flowId(), Optional.ofNullable(command.flowRevision()))
                .orElseThrow(() -> new FlowNotFoundException(command.executionFullId(), command.flowRevision()));

            var newExecution = executionService.create(command, flow);

            var persisted = persistNewExecutionWithKillSwitch(newExecution);
            if (persisted.isEmpty()) {
                return Optional.empty();
            }
            newExecution = persisted.get();

            // A terminal execution (e.g. a trigger that failed to render its inputs) needs no
            // further processing by the executor — it is already in its final state.
            if (newExecution.getState().isTerminated()) {
                return Optional.empty();
            }

            var eventType = newExecution.getState().isCreated() ? ExecutionEventType.CREATED : ExecutionEventType.UPDATED;
            return executionEventMessageHandler.handle(new ExecutionEvent(newExecution, eventType));
        } catch (Exception e) {
            log.error(
                "Unable to process Create command for execution {}: ignoring command with eventId {}",
                command.executionId(), command.eventId(), e
            );
            outcome = AsyncOperationProcessedEvent.Outcome.FAILED;
            error = e.getMessage();
            return Optional.empty();
        } finally {
            asyncOperationService.emitProcessedIfAsync(command, command.tenantId(), command.executionId(), outcome, error);
        }
    }

    private Optional<ExecutorContext> handleReplay(Replay command) {
        AsyncOperationProcessedEvent.Outcome outcome = AsyncOperationProcessedEvent.Outcome.SUCCEEDED;
        String error = null;
        try {
            var raw = executionStateStore.findById(command.sourceExecutionId());
            if (raw == null) {
                throw new IllegalStateException("Source execution not found: " + command.sourceExecutionId());
            }

            // Apply inputs override if the controller merged new inputs before emitting the command
            final var sourceExecution = command.inputs() != null ? raw.withInputs(command.inputs()) : raw;

            // findByExecutionThenInjectDefaults returns FlowWithSource (a Flow subtype);
            // findById returns FlowInterface — cast is safe since all concrete implementations return Flow.
            Flow flow;
            if (command.revision() != null) {
                flow = (Flow) flowMetaStore
                    .findById(command.tenantId(), command.namespace(), command.flowId(), Optional.of(command.revision()))
                    .orElseThrow(() -> new FlowNotFoundException(sourceExecution));
            } else {
                flow = flowMetaStore
                    .findByExecutionThenInjectDefaults(sourceExecution)
                    .orElseThrow(() -> new FlowNotFoundException(sourceExecution));
            }

            var newExecution = executionService.replay(
                sourceExecution,
                flow,
                command.taskRunId(),
                command.revision(),
                Optional.ofNullable(command.breakpoints()),
                true,
                command.executionId()
            );

            if (persistNewExecutionWithKillSwitch(newExecution).isEmpty()) {
                return Optional.empty();
            }

            return executionEventMessageHandler.handle(new ExecutionEvent(newExecution, ExecutionEventType.CREATED));
        } catch (Exception e) {
            log.error(
                "Unable to process Replay command for new execution {}: ignoring command with eventId {}",
                command.executionId(), command.eventId(), e
            );
            outcome = AsyncOperationProcessedEvent.Outcome.FAILED;
            error = e.getMessage();
            return Optional.empty();
        } finally {
            asyncOperationService.emitProcessedIfAsync(command, command.tenantId(), command.executionId(), outcome, error);
        }
    }

    /**
     * Evaluates the kill switch on a brand-new (not yet persisted) execution and writes it to the state
     * store in the appropriate terminal state if kill-switched.
     *
     * <p>A brand-new execution has no running tasks, so it can be written directly in a terminal state
     * without going through the KILLING intermediate state or the kill queue.</p>
     *
     * @return {@code Optional.of(execution)} if the execution should proceed to normal processing;
     *         {@code Optional.empty()} if it was kill-switched (already persisted in its terminal state).
     */
    private Optional<Execution> persistNewExecutionWithKillSwitch(Execution newExecution) {
        EvaluationType evaluationType = killSwitchService.evaluate(newExecution);
        if (evaluationType == EvaluationType.KILL) {
            log.warn("Kill switch active (KILL): killing execution {}", newExecution.getId());
            executionStateStore.create(newExecution.withState(State.Type.KILLED).addLabel(new Label(Label.KILL_SWITCH, "killed")));
            return Optional.empty();
        }
        if (evaluationType == EvaluationType.CANCEL) {
            log.warn("Kill switch active (CANCEL): cancelling execution {}", newExecution.getId());
            executionStateStore.create(newExecution.withState(State.Type.CANCELLED).addLabel(new Label(Label.KILL_SWITCH, "cancelled")));
            return Optional.empty();
        }
        executionStateStore.create(newExecution);
        if (evaluationType == EvaluationType.IGNORE) {
            log.warn("Kill switch active (IGNORE): ignoring execution {}", newExecution.getId());
            return Optional.empty();
        }
        return Optional.of(newExecution);
    }

    /**
     * Pre-2.0 backward compatibility: if a task run carries inline outputs (deprecated {@code Variables outputs} field),
     * persist them into the task output repository so the rest of the executor can work uniformly with the modern storage.
     */
    @SuppressWarnings("deprecation")
    private Execution migrateTaskOutputs(Execution execution) throws InternalException {
        if (ListUtils.isEmpty(execution.getTaskRunList())) {
            return execution;
        }

        for (TaskRun taskRun : execution.getTaskRunList()) {
            if (taskRun.getOutputs() != null) {
                taskOutputService.saveOutputs(taskRun, taskRun.getOutputs());
                execution = execution.withTaskRun(taskRun.clearOutputs());
            }
        }
        return execution;
    }
}
