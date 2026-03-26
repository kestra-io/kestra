package io.kestra.core.scheduler.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.kestra.core.events.CrudEvent;
import io.kestra.core.models.executions.Execution;
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
    private final DispatchQueueInterface<Execution> executionQueue;
    private final RunContextLoggerFactory runContextLoggerFactory;

    @Inject
    public DefaultTriggerExecutionPublisher(ApplicationEventPublisher<CrudEvent<Execution>> executionEventPublisher,
        DispatchQueueInterface<Execution> executionQueue,
        RunContextLoggerFactory runContextLoggerFactory) {
        this.executionEventPublisher = executionEventPublisher;
        this.executionQueue = executionQueue;
        this.runContextLoggerFactory = runContextLoggerFactory;
    }

    public void send(final Execution execution) {
        try {
            this.executionQueue.emit(execution);
            this.executionEventPublisher.publishEvent(CrudEvent.create(execution));
        } catch (QueueException e) {
            try {
                Execution failedExecution = fail(execution, e);
                this.executionQueue.emit(failedExecution);
                this.executionEventPublisher.publishEvent(CrudEvent.create(execution));
            } catch (QueueException ex) {
                LOG.error("Unable to emit the execution", ex);
            }
        }
    }

    private Execution fail(Execution message, Exception e) {
        var failedExecution = message.failedExecutionFromExecutor(e);
        var logger = runContextLoggerFactory.create(message);
        logger.emitLogs(failedExecution.logs());
        return failedExecution.execution().getState().isFailed() ? failedExecution.execution() : failedExecution.execution().withState(State.Type.FAILED);
    }

}
