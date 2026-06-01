package io.kestra.executor.handler;

import java.util.Optional;

import io.kestra.core.killswitch.EvaluationType;
import io.kestra.core.killswitch.KillSwitchService;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.runners.ExecutionEvent;
import io.kestra.core.runners.ExecutionEventType;
import io.kestra.executor.ExecutionStateStore;
import io.kestra.executor.ExecutorContext;
import io.kestra.executor.ExecutorMessageHandler;
import io.kestra.executor.KillSwitchActionService;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Handles {@link Execution} messages from the legacy execution queue.
 *
 * <p>Executions arriving on this queue are already fully built (subflow executions, loop
 * executions, concurrency-queue pops). This handler persists the execution unconditionally —
 * even if it is later kill-switched — so it always appears in the database, then applies
 * the kill-switch guard before delegating to normal processing.</p>
 */
@Singleton
@Slf4j
public class ExecutionMessageHandler implements ExecutorMessageHandler<Execution> {
    private final ExecutionStateStore executionStateStore;
    private final KillSwitchService killSwitchService;
    private final KillSwitchActionService killSwitchActionService;
    private final ExecutionEventMessageHandler executionEventMessageHandler;

    @Inject
    public ExecutionMessageHandler(
        ExecutionStateStore executionStateStore,
        KillSwitchService killSwitchService,
        KillSwitchActionService killSwitchActionService,
        ExecutionEventMessageHandler executionEventMessageHandler) {
        this.executionStateStore = executionStateStore;
        this.killSwitchService = killSwitchService;
        this.killSwitchActionService = killSwitchActionService;
        this.executionEventMessageHandler = executionEventMessageHandler;
    }

    @Override
    public Optional<ExecutorContext> handle(Execution message) {
        // Always persist first so the execution is present in the DB even if kill-switched.
        try {
            executionStateStore.create(message);
        } catch (Exception e) {
            log.error("Unable to create execution {}", message.getId(), e);
        }

        EvaluationType evaluationType = killSwitchService.evaluate(message);
        if (evaluationType.isKillSwitched(message)) {
            killSwitchActionService.handle(evaluationType, message.getTenantId(), message.getId());
            return Optional.empty();
        }

        var eventType = message.getState().isCreated() ? ExecutionEventType.CREATED : ExecutionEventType.UPDATED;
        return executionEventMessageHandler.handle(new ExecutionEvent(message, eventType));
    }
}
