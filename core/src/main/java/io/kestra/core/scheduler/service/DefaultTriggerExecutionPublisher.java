package io.kestra.core.scheduler.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.kestra.core.events.CrudEvent;
import io.kestra.core.executor.command.Create;
import io.kestra.core.executor.command.ExecutionCommand;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionId;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.runners.RunContextLoggerFactory;

import io.micronaut.context.event.ApplicationEventPublisher;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class DefaultTriggerExecutionPublisher implements TriggerExecutionPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultTriggerExecutionPublisher.class);

    private final ApplicationEventPublisher<CrudEvent<Execution>> executionEventPublisher;
    private final DispatchQueueInterface<ExecutionCommand> executionCommandQueue;
    private final RunContextLoggerFactory runContextLoggerFactory;

    @Inject
    public DefaultTriggerExecutionPublisher(ApplicationEventPublisher<CrudEvent<Execution>> executionEventPublisher,
        DispatchQueueInterface<ExecutionCommand> executionCommandQueue,
        RunContextLoggerFactory runContextLoggerFactory) {
        this.executionEventPublisher = executionEventPublisher;
        this.executionCommandQueue = executionCommandQueue;
        this.runContextLoggerFactory = runContextLoggerFactory;
    }

    public void send(final Execution execution) {
        try {
            Create cmd = toCreate(execution);
            // If the trigger already produced a terminal execution (e.g. input rendering failed),
            // preserve that state so the executor doesn't restart it from CREATED.
            if (execution.getState().isTerminated()) {
                cmd = cmd.withStateType(execution.getState().getCurrent());
            }
            this.executionCommandQueue.emit(cmd);
        } catch (QueueException e) {
            try {
                Execution failedExecution = fail(execution, e);
                this.executionCommandQueue.emit(toCreate(failedExecution).withStateType(State.Type.FAILED));
            } catch (QueueException ex) {
                LOG.error("Unable to emit the execution", ex);
            }
        }
    }

    private Create toCreate(Execution execution) {
        return Create.of(new ExecutionId(execution.getTenantId(), execution.getNamespace(), execution.getFlowId(), execution.getId(), execution.getFlowRevision()))
            .withKind(execution.getKind())
            .withTrigger(execution.getTrigger())
            .withLabels(execution.getLabels())
            .withInputs(execution.getInputs())
            .withScheduleDate(execution.getScheduleDate());
    }

    private Execution fail(Execution message, Exception e) {
        var failedExecution = message.failedExecutionFromExecutor(e);
        var logger = runContextLoggerFactory.create(message);
        logger.emitLogs(failedExecution.logs());
        return failedExecution.execution().getState().isFailed() ? failedExecution.execution() : failedExecution.execution().withState(State.Type.FAILED);
    }

}
