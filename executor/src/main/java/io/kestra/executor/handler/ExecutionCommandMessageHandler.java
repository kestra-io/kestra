package io.kestra.executor.handler;

import java.util.Optional;

import io.kestra.core.exceptions.FlowNotFoundException;
import io.kestra.core.executor.command.*;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.FlowMetaStoreInterface;
import io.kestra.core.services.ExecutionService;
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

    @Inject
    public ExecutionCommandMessageHandler(ExecutionService executionService,
        ExecutionStateStore executionStateStore,
        FlowMetaStoreInterface flowMetaStore) {
        this.executionService = executionService;
        this.executionStateStore = executionStateStore;
        this.flowMetaStore = flowMetaStore;
    }

    @Override
    public Optional<ExecutorContext> handle(ExecutionCommand message) {
        return executionStateStore.lock(message.executionId(), execution ->
        {
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
                return newExecution != null ? executorContext.withExecution(newExecution, "ExecutionCommandMessageHandler") : null;
            } catch (FlowNotFoundException e) {
                // FIXME we ignore commands for flows that are not found: is it the right thing to do?
                //  we may instead fail the execution
                log.error("Unable to find flow for execution {}: ignoring {} command with eventId {}", message.executionId(), message.getClass().getSimpleName(), message.eventId(), e);
                return null;
            } catch (Exception e) {
                // FIXME: the execution service throws an unexpected error: should we really fail fast?
                throw new RuntimeException(e);
            }
        });
    }
}
