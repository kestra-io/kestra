package io.kestra.webserver.services.ai.agent.tool;

import java.util.concurrent.TimeoutException;

import io.kestra.core.async.AsyncOperationProcessedEvent;
import io.kestra.core.async.AsyncOperationsConfiguration;
import io.kestra.core.events.Actor;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.queues.QueueException;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.services.AsyncOperationWaiter;
import io.kestra.webserver.services.ActorResolver;

import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Loads the execution an agent tool acts on and submits the tool's command to the executor, blocking until the
 * executor has accepted or rejected it, the way the execution endpoints do.
 */
@Singleton
public class ExecutionActionSubmitter {
    private final ExecutionRepositoryInterface executionRepository;
    private final AsyncOperationWaiter asyncOperationWaiter;
    private final AsyncOperationsConfiguration asyncOperationsConfiguration;
    private final ActorResolver actorResolver;

    @Inject
    public ExecutionActionSubmitter(
        final ExecutionRepositoryInterface executionRepository,
        final AsyncOperationWaiter asyncOperationWaiter,
        final AsyncOperationsConfiguration asyncOperationsConfiguration,
        final ActorResolver actorResolver) {
        this.executionRepository = executionRepository;
        this.asyncOperationWaiter = asyncOperationWaiter;
        this.asyncOperationsConfiguration = asyncOperationsConfiguration;
        this.actorResolver = actorResolver;
    }

    /**
     * @throws IllegalArgumentException if the execution does not exist in the tenant
     */
    public Execution findExecution(final String tenant, final String executionId) {
        return executionRepository.findById(tenant, executionId)
            .orElseThrow(() -> new IllegalArgumentException("Execution not found: '%s'".formatted(executionId)));
    }

    /**
     * @param action the action named in error messages, e.g. "kill"
     * @throws IllegalStateException if the executor rejects the command or does not answer in time
     */
    public void submitAndWait(final String executionId, final String action, final Emitter emitter) {
        Actor actor = actorResolver.resolve();
        AsyncOperationProcessedEvent processed;
        try {
            processed = asyncOperationWaiter.submitAndWait(
                executionId,
                operationId ->
                {
                    try {
                        emitter.emit(operationId, actor);
                    } catch (QueueException e) {
                        throw new IllegalStateException("Unable to submit the %s of execution '%s'.".formatted(action, executionId), e);
                    }
                },
                asyncOperationsConfiguration.waitTimeout()
            );
        } catch (TimeoutException e) {
            throw new IllegalStateException("Timed out waiting for the %s of execution '%s' to be accepted.".formatted(action, executionId), e);
        }

        if (processed.outcome() == AsyncOperationProcessedEvent.Outcome.FAILED) {
            throw new IllegalStateException("Failed to %s execution '%s': %s".formatted(action, executionId, processed.error()));
        }
    }

    @FunctionalInterface
    public interface Emitter {
        void emit(String operationId, @Nullable Actor actor) throws QueueException;
    }
}
