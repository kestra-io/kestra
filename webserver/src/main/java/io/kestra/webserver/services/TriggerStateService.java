package io.kestra.webserver.services;

import java.time.Duration;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import io.kestra.core.async.AsyncOperationProcessedEvent;
import io.kestra.core.exceptions.ConflictException;
import io.kestra.core.exceptions.NotFoundException;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.executions.ExecutionKilled;
import io.kestra.core.models.executions.ExecutionKilledTrigger;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.triggers.TriggerId;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.repositories.TriggerRepositoryInterface;
import io.kestra.core.scheduler.events.CreateBackfillTrigger;
import io.kestra.core.scheduler.events.DeleteBackfillTrigger;
import io.kestra.core.scheduler.events.ResetTrigger;
import io.kestra.core.scheduler.events.SetDisableTrigger;
import io.kestra.core.scheduler.events.SetPauseBackfillTrigger;
import io.kestra.core.scheduler.events.TriggerDeleted;
import io.kestra.core.scheduler.model.TriggerState;
import io.kestra.core.scheduler.queue.TriggerEventQueue;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.services.AsyncOperationWaiter;
import io.kestra.core.async.AsyncOperationsConfiguration;
import io.kestra.webserver.models.api.ApiAsyncOperationResponse;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Service for managing {@link TriggerState}.
 * <p>
 * Single-action methods block until the emitted domain event is processed (as reported by a matching
 * {@link AsyncOperationProcessedEvent}) and return the refreshed {@link TriggerState}.
 * Bulk methods emit all events under a single {@code operationId} and return an
 * {@link ApiAsyncOperationResponse} so callers can correlate progress.
 */
@Singleton
public class TriggerStateService {

    private final TriggerRepositoryInterface triggerRepository;
    private final FlowRepositoryInterface flowRepository;
    private final TriggerEventQueue triggerEventQueue;
    private final BroadcastQueueInterface<ExecutionKilled> executionKilledQueue;
    private final AsyncOperationWaiter asyncOperationWaiter;
    private final Duration asyncWaitTimeout;

    @Inject
    public TriggerStateService(final TriggerRepositoryInterface triggerRepository,
        final FlowRepositoryInterface flowRepository,
        final TriggerEventQueue triggerEventQueue,
        final BroadcastQueueInterface<ExecutionKilled> executionKilledQueue,
        final AsyncOperationWaiter asyncOperationWaiter,
        final AsyncOperationsConfiguration asyncOperationsConfiguration) {
        this.triggerRepository = triggerRepository;
        this.flowRepository = flowRepository;
        this.triggerEventQueue = triggerEventQueue;
        this.executionKilledQueue = executionKilledQueue;
        this.asyncOperationWaiter = asyncOperationWaiter;
        this.asyncWaitTimeout = asyncOperationsConfiguration.waitTimeout();
    }

    /**
     * Unlocks a trigger and waits for the scheduler to acknowledge the state change.
     *
     * @param trigger the trigger identifier.
     * @return the refreshed trigger state.
     * @throws NotFoundException if the trigger does not exist.
     * @throws ConflictException if the trigger is already unlocked or the reset failed.
     */
    public TriggerState unlockTriggerById(final TriggerId trigger) throws NotFoundException, ConflictException {
        TriggerState state = getTriggerState(trigger);
        if (!state.isLocked()) {
            throw new ConflictException("trigger %s is already unlocked".formatted(trigger));
        }
        awaitBlockingAction(
            trigger.uid(),
            operationId -> triggerEventQueue.send(new ResetTrigger(trigger).withOperationId(operationId)),
            "Unlock"
        );
        return refresh(trigger, "unlock");
    }

    /**
     * Unlocks all locked triggers among the given identifiers. Non-existing and already-unlocked
     * triggers are silently skipped.
     *
     * @param triggers the trigger identifiers.
     * @return an async-operation response with the count of unlock events emitted.
     */
    public ApiAsyncOperationResponse unlockAllByIds(List<TriggerId> triggers) {
        List<TriggerId> lockedIds = triggers.stream()
            .filter(id -> triggerRepository.findById(id).map(TriggerState::isLocked).orElse(false))
            .toList();
        return submitBatch(
            lockedIds, (id, operationId) -> triggerEventQueue.send(new ResetTrigger(id).withOperationId(operationId))
        );
    }

    /**
     * Unlocks all locked triggers matching the given filters.
     *
     * @param tenant the tenant identifier.
     * @param filters the query filters.
     * @return an async-operation response with the count of unlock events emitted.
     */
    public ApiAsyncOperationResponse unlockAllMatching(String tenant, List<QueryFilter> filters) {
        List<TriggerId> lockedIds = triggerRepository.find(tenant, filters)
            .filter(TriggerState::isLocked)
            .map(TriggerId::of)
            .collectList()
            .blockOptional()
            .orElse(List.of());
        return submitBatch(
            lockedIds, (id, operationId) -> triggerEventQueue.send(new ResetTrigger(id).withOperationId(operationId))
        );
    }

    /**
     * Resets a trigger: kills any in-flight execution and waits for the scheduler to acknowledge
     * the reset.
     *
     * @param triggerId the trigger identifier.
     * @return the refreshed trigger state.
     * @throws NotFoundException if the trigger does not exist.
     * @throws QueueException if the execution-killed event cannot be emitted.
     * @throws ConflictException if the reset failed.
     */
    public TriggerState resetTrigger(final TriggerId triggerId) throws NotFoundException, QueueException, ConflictException {
        getTriggerState(triggerId);
        executionKilledQueue.emit(
            ExecutionKilledTrigger.builder()
                // Trigger kills are not processed by the Executor: emit them directly in the
                // EXECUTED state, the only state forwarded to the workers.
                .state(ExecutionKilled.State.EXECUTED)
                .tenantId(triggerId.getTenantId())
                .namespace(triggerId.getNamespace())
                .flowId(triggerId.getFlowId())
                .triggerId(triggerId.getTriggerId())
                .build()
        );
        awaitBlockingAction(
            triggerId.uid(),
            operationId -> triggerEventQueue.send(new ResetTrigger(triggerId).withOperationId(operationId)),
            "Restart"
        );
        return refresh(triggerId, "restart");
    }

    /**
     * Creates a backfill and waits for the scheduler to acknowledge.
     *
     * @throws NotFoundException if the trigger does not exist.
     * @throws ConflictException if the backfill cannot be created.
     */
    public TriggerState createBackfill(TriggerId triggerId, CreateBackfillTrigger.Backfill backfill) throws NotFoundException, ConflictException {
        getTriggerState(triggerId);
        awaitBlockingAction(
            triggerId.uid(),
            operationId -> triggerEventQueue.send(new CreateBackfillTrigger(triggerId, backfill).withOperationId(operationId)),
            "Create backfill"
        );
        return refresh(triggerId, "create backfill");
    }

    /**
     * Pauses or resumes a backfill and waits for the scheduler to acknowledge.
     *
     * @throws NotFoundException if the trigger does not exist.
     * @throws ConflictException if the backfill cannot be updated.
     */
    public TriggerState setBackfillPaused(TriggerId triggerId, boolean paused) throws NotFoundException, ConflictException {
        getTriggerState(triggerId);
        awaitBlockingAction(
            triggerId.uid(),
            operationId -> triggerEventQueue.send(new SetPauseBackfillTrigger(triggerId, paused).withOperationId(operationId)),
            paused ? "Pause backfill" : "Unpause backfill"
        );
        return refresh(triggerId, paused ? "pause backfill" : "unpause backfill");
    }

    /**
     * Pauses backfills for the given triggers. Non-existing triggers are silently skipped.
     */
    public ApiAsyncOperationResponse pauseAllBackfillsByIds(List<TriggerId> triggers) {
        return submitExistingBatch(
            triggers, (id, operationId) -> triggerEventQueue.send(new SetPauseBackfillTrigger(id, true).withOperationId(operationId))
        );
    }

    /**
     * Pauses backfills for triggers matching the given filters.
     */
    public ApiAsyncOperationResponse pauseAllBackfillsMatching(String tenant, List<QueryFilter> filters) {
        return submitMatching(
            tenant, filters, (id, operationId) -> triggerEventQueue.send(new SetPauseBackfillTrigger(id, true).withOperationId(operationId))
        );
    }

    /**
     * Resumes backfills for the given triggers. Non-existing triggers are silently skipped.
     */
    public ApiAsyncOperationResponse resumeAllBackfillsByIds(List<TriggerId> triggers) {
        return submitExistingBatch(
            triggers, (id, operationId) -> triggerEventQueue.send(new SetPauseBackfillTrigger(id, false).withOperationId(operationId))
        );
    }

    /**
     * Resumes backfills for triggers matching the given filters.
     */
    public ApiAsyncOperationResponse resumeAllBackfillsMatching(String tenant, List<QueryFilter> filters) {
        return submitMatching(
            tenant, filters, (id, operationId) -> triggerEventQueue.send(new SetPauseBackfillTrigger(id, false).withOperationId(operationId))
        );
    }

    /**
     * Deletes the backfill for the given trigger and waits for the scheduler to acknowledge.
     *
     * @throws NotFoundException if the trigger does not exist.
     * @throws ConflictException if the backfill cannot be deleted.
     */
    public TriggerState deleteBackfill(TriggerId triggerId) throws NotFoundException, ConflictException {
        getTriggerState(triggerId);
        awaitBlockingAction(
            triggerId.uid(),
            operationId -> triggerEventQueue.send(new DeleteBackfillTrigger(triggerId).withOperationId(operationId)),
            "Delete backfill"
        );
        return refresh(triggerId, "delete backfill");
    }

    /**
     * Deletes backfills for the given triggers. Non-existing triggers are silently skipped.
     */
    public ApiAsyncOperationResponse deleteAllBackfillsByIds(List<TriggerId> triggers) {
        return submitExistingBatch(
            triggers, (id, operationId) -> triggerEventQueue.send(new DeleteBackfillTrigger(id).withOperationId(operationId))
        );
    }

    /**
     * Deletes backfills for triggers matching the given filters.
     */
    public ApiAsyncOperationResponse deleteAllBackfillsMatching(String tenant, List<QueryFilter> filters) {
        return submitMatching(
            tenant, filters, (id, operationId) -> triggerEventQueue.send(new DeleteBackfillTrigger(id).withOperationId(operationId))
        );
    }

    /**
     * Deletes the trigger and waits for the scheduler to acknowledge.
     *
     * @throws NotFoundException if the trigger does not exist.
     * @throws ConflictException if the trigger cannot be deleted.
     */
    public void deleteById(TriggerId trigger) throws NotFoundException, ConflictException {
        getTriggerState(trigger);
        awaitBlockingAction(
            trigger.uid(),
            operationId -> triggerEventQueue.send(new TriggerDeleted(trigger).withOperationId(operationId)),
            "Delete"
        );
    }

    /**
     * Deletes all triggers for the given identifiers. Non-existing triggers are silently skipped.
     */
    public ApiAsyncOperationResponse deleteAllByIds(List<TriggerId> triggers) {
        return submitExistingBatch(
            triggers, (id, operationId) -> triggerEventQueue.send(new TriggerDeleted(id).withOperationId(operationId))
        );
    }

    /**
     * Deletes all triggers matching the given filters.
     */
    public ApiAsyncOperationResponse deleteAllMatching(String tenant, List<QueryFilter> filters) {
        return submitMatching(
            tenant, filters, (id, operationId) -> triggerEventQueue.send(new TriggerDeleted(id).withOperationId(operationId))
        );
    }

    /**
     * Enables or disables a trigger and waits for the scheduler to acknowledge.
     *
     * @throws NotFoundException if the flow or trigger does not exist.
     * @throws ConflictException if the change failed.
     */
    public TriggerState toggleTriggerById(TriggerId trigger, boolean disabled) throws NotFoundException, ConflictException {
        validateToggleable(trigger);
        awaitBlockingAction(
            trigger.uid(),
            operationId -> triggerEventQueue.send(new SetDisableTrigger(trigger, disabled).withOperationId(operationId)),
            "Set disabled"
        );
        return refresh(trigger, "set-disabled");
    }

    /**
     * Enables or disables the given triggers. Missing triggers are silently skipped.
     */
    public ApiAsyncOperationResponse toggleAllByIds(List<TriggerId> triggers, boolean disabled) {
        List<TriggerId> toggleable = triggers.stream()
            .filter(id ->
            {
                try {
                    validateToggleable(id);
                    return true;
                } catch (NotFoundException e) {
                    return false;
                }
            })
            .toList();
        return submitBatch(
            toggleable, (id, operationId) -> triggerEventQueue.send(new SetDisableTrigger(id, disabled).withOperationId(operationId))
        );
    }

    /**
     * Enables or disables triggers matching the given filters.
     */
    public ApiAsyncOperationResponse toggleAllMatching(String tenant, List<QueryFilter> filters, boolean disabled) {
        String operationId = IdUtils.create();
        int count = triggerRepository.find(tenant, filters)
            .map(trigger ->
            {
                TriggerId id = TriggerId.of(trigger);
                try {
                    validateToggleable(id);
                    triggerEventQueue.send(new SetDisableTrigger(id, disabled).withOperationId(operationId));
                    return 1;
                } catch (NotFoundException ignored) {
                    return 0;
                }
            })
            .reduce(Integer::sum)
            .blockOptional()
            .orElse(0);
        return new ApiAsyncOperationResponse(operationId, count);
    }

    private TriggerState getTriggerState(TriggerId triggerId) throws NotFoundException {
        return triggerRepository.findById(triggerId)
            .orElseThrow(() -> new NotFoundException("Trigger %s not found".formatted(triggerId)));
    }

    private TriggerState refresh(TriggerId triggerId, String action) {
        return triggerRepository.findById(triggerId)
            .orElseThrow(() -> new NoSuchElementException("Trigger disappeared after " + action + ": " + triggerId));
    }

    private void validateToggleable(TriggerId triggerId) throws NotFoundException {
        Flow flow = flowRepository.findById(triggerId.getTenantId(), triggerId.getNamespace(), triggerId.getFlowId())
            .orElseThrow(() -> new NotFoundException("Flow not found for trigger: %s".formatted(triggerId)));

        flow.getTriggers().stream()
            .filter(t -> t.getId().equals(triggerId.getTriggerId()))
            .findFirst()
            .orElseThrow(() -> new NotFoundException("Trigger not found: %s".formatted(triggerId)));
    }

    private ApiAsyncOperationResponse submitExistingBatch(List<TriggerId> triggers, java.util.function.BiConsumer<TriggerId, String> emit) {
        List<TriggerId> existing = triggers.stream()
            .filter(id -> triggerRepository.findById(id).isPresent())
            .toList();
        return submitBatch(existing, emit);
    }

    private ApiAsyncOperationResponse submitBatch(List<TriggerId> triggers, java.util.function.BiConsumer<TriggerId, String> emit) {
        String operationId = IdUtils.create();
        for (TriggerId id : triggers) {
            emit.accept(id, operationId);
        }
        return new ApiAsyncOperationResponse(operationId, triggers.size());
    }

    private ApiAsyncOperationResponse submitMatching(String tenant, List<QueryFilter> filters, java.util.function.BiConsumer<TriggerId, String> emit) {
        List<TriggerId> ids = triggerRepository.find(tenant, filters)
            .map(TriggerId::of)
            .collectList()
            .blockOptional()
            .orElse(List.of());
        return submitBatch(ids, emit);
    }

    /**
     * Submits a trigger event tagged with a fresh {@code operationId} and blocks up to
     * {@link #asyncWaitTimeout} for the matching {@link AsyncOperationProcessedEvent}.
     * <p>
     * Maps the outcome to HTTP semantics:
     * <ul>
     * <li>{@code TimeoutException} → {@link HttpStatus#GATEWAY_TIMEOUT} (504);</li>
     * <li>{@code FAILED} outcome → {@link ConflictException} (409).</li>
     * </ul>
     */
    private void awaitBlockingAction(String itemId, Consumer<String> submit, String actionLabel) throws ConflictException {
        AsyncOperationProcessedEvent processed;
        try {
            processed = asyncOperationWaiter.submitAndWait(itemId, submit, asyncWaitTimeout);
        } catch (TimeoutException e) {
            throw new HttpStatusException(HttpStatus.GATEWAY_TIMEOUT, "Operation timed out waiting for state transition");
        }
        if (processed.outcome() == AsyncOperationProcessedEvent.Outcome.FAILED) {
            throw new ConflictException(actionLabel + " failed: " + processed.error());
        }
    }
}
