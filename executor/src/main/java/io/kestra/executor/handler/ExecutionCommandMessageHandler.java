package io.kestra.executor.handler;

import java.util.Optional;

import io.kestra.core.exceptions.FlowNotFoundException;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.executor.command.*;
import io.kestra.core.async.AsyncOperationProcessedEvent;
import io.kestra.core.async.AsyncOperationService;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;
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

    @Inject
    public ExecutionCommandMessageHandler(ExecutionService executionService,
        ExecutionStateStore executionStateStore,
        FlowMetaStoreInterface flowMetaStore,
        TaskOutputService taskOutputService,
        AsyncOperationService asyncOperationService) {
        this.executionService = executionService;
        this.executionStateStore = executionStateStore;
        this.flowMetaStore = flowMetaStore;
        this.taskOutputService = taskOutputService;
        this.asyncOperationService = asyncOperationService;
    }

    @Override
    public Optional<ExecutorContext> handle(ExecutionCommand message) {
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
