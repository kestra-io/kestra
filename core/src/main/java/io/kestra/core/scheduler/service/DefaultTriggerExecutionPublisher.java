package io.kestra.core.scheduler.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.kestra.core.executor.command.Create;
import io.kestra.core.executor.command.ExecutionCommand;
import io.kestra.core.models.executions.ExecutionId;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.triggers.TriggerEvaluationResult;
import io.kestra.core.models.triggers.TriggerId;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.runners.RunContextLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class DefaultTriggerExecutionPublisher implements TriggerExecutionPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultTriggerExecutionPublisher.class);

    private final DispatchQueueInterface<ExecutionCommand> executionCommandQueue;
    private final RunContextLoggerFactory runContextLoggerFactory;

    @Inject
    public DefaultTriggerExecutionPublisher(DispatchQueueInterface<ExecutionCommand> executionCommandQueue,
        RunContextLoggerFactory runContextLoggerFactory) {
        this.executionCommandQueue = executionCommandQueue;
        this.runContextLoggerFactory = runContextLoggerFactory;
    }

    @Override
    public void send(final TriggerId triggerId, final TriggerEvaluationResult evaluation) {
        Create command = toCreate(triggerId, evaluation);
        try {
            this.executionCommandQueue.emit(command);
        } catch (QueueException e) {
            // The command queue is down, so attach the cause to the execution before retrying a FAILED one:
            // logs go through their own queue and are often the only trace the user gets.
            runContextLoggerFactory.create(command.executionFullId(), command.kind(), command.labels())
                .logger()
                .error("Unable to emit the execution to the executor.", e);
            try {
                this.executionCommandQueue.emit(command.withStateType(State.Type.FAILED));
            } catch (QueueException ex) {
                LOG.error("Unable to emit the execution", ex);
            }
        }
    }

    private Create toCreate(TriggerId triggerId, TriggerEvaluationResult evaluation) {
        ExecutionId executionId = new ExecutionId(
            triggerId.getTenantId(),
            triggerId.getNamespace(),
            triggerId.getFlowId(),
            evaluation.executionId(),
            evaluation.flowRevision()
        );

        // A non-terminal evaluation leaves the state unset so the executor starts the execution from CREATED;
        // a terminal one (e.g. input rendering failed) is preserved so the executor doesn't restart it.
        State.Type stateType = evaluation.stateType() != null && evaluation.stateType().isTerminated()
            ? evaluation.stateType()
            : null;

        return Create.of(executionId)
            .withStateType(stateType)
            .withTrigger(evaluation.trigger())
            .withLabels(evaluation.labels())
            .withInputs(evaluation.inputs())
            .withScheduleDate(evaluation.scheduleDate());
    }
}
