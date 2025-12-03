package io.kestra.core.services;

import io.kestra.core.exceptions.ConflictException;
import io.kestra.core.exceptions.NotFoundException;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.executions.ExecutionKilled;
import io.kestra.core.models.executions.ExecutionKilledTrigger;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.RealtimeTriggerInterface;
import io.kestra.core.models.triggers.TriggerId;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.repositories.TriggerRepositoryInterface;
import io.kestra.core.scheduler.TriggerEventQueue;
import io.kestra.core.scheduler.events.CreateBackfillTrigger;
import io.kestra.core.scheduler.events.DeleteBackfillTrigger;
import io.kestra.core.scheduler.events.ResetTrigger;
import io.kestra.core.scheduler.events.SetDisableTrigger;
import io.kestra.core.scheduler.events.SetPauseBackfillTrigger;
import io.kestra.core.scheduler.model.TriggerState;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;

import java.time.Clock;
import java.util.List;

import static io.kestra.core.utils.Rethrow.throwFunction;

/**
 * Service for managing {@link TriggerState}.
 */
@Singleton
public class TriggerStateService {

    private final TriggerRepositoryInterface triggerRepository;
    private final FlowRepositoryInterface flowRepository;
    private final TriggerEventQueue triggerEventQueue;
    private final QueueInterface<ExecutionKilled> executionKilledQueue;

    @Inject
    public TriggerStateService(final TriggerRepositoryInterface triggerRepository,
                               final FlowRepositoryInterface flowRepository,
                               final TriggerEventQueue triggerEventQueue,
                               final QueueInterface<ExecutionKilled> executionKilledQueue) {
        this.triggerRepository = triggerRepository;
        this.triggerEventQueue = triggerEventQueue;
        this.executionKilledQueue = executionKilledQueue;
        this.flowRepository = flowRepository;
    }

    /**
     * Toggles all triggers matching the given filters.
     *
     * @param tenant  the tenant identifier.
     * @param filters the filters to match triggers.
     * @throws NotFoundException if no trigger can be found.
     */
    public int toggleAllTriggersMatching(String tenant, List<QueryFilter> filters, boolean disabled) throws NotFoundException {
        return triggerRepository.find(tenant, filters).map(throwFunction(trigger -> {
                try {
                    toggleTriggerById(trigger, disabled);
                    return 1;
                } catch (ConflictException e) {
                    return 0;
                }
            }))
            .reduce(Integer::sum)
            .blockOptional()
            .orElse(0);
    }

    /**
     * Toggles the trigger for given identifier.
     *
     * @param trigger the trigger identifier.
     * @throws NotFoundException if no flow or trigger can be found.
     */
    public void toggleTriggerById(TriggerId trigger, boolean disabled) throws NotFoundException, ConflictException {
        Flow flow = this.flowRepository.findById(trigger.getTenantId(), trigger.getNamespace(), trigger.getFlowId())
            .orElseThrow(() -> new NotFoundException("Flow not found for trigger: %s".formatted(trigger)));

        AbstractTrigger abstractTrigger = flow.getTriggers().stream().filter(t -> t.getId().equals(trigger.getTriggerId())).findFirst()
            .orElseThrow(() -> new NotFoundException("Trigger not found: %s".formatted(trigger)));

        if (abstractTrigger instanceof RealtimeTriggerInterface) {
            throw new ConflictException("Realtime triggers can not be updated through the API, please edit the trigger from the flow.");
        }

        triggerEventQueue.send(new SetDisableTrigger(TriggerId.of(trigger), disabled));
    }

    public int deleteAllBackfills(List<TriggerId> triggers) {
        return backfillsAction(Flux.fromIterable(triggers), BackfillAction.DELETE);
    }

    /**
     * Deletes all backfills for triggers matching the given filters.
     *
     * @param tenant  the tenant identifier.
     * @param filters the filters to match triggers.
     * @throws NotFoundException if no trigger can be found.
     */
    public int deleteBackfillMatching(String tenant, List<QueryFilter> filters) {
        return backfillsAction(triggerRepository.find(tenant, filters), BackfillAction.DELETE);
    }

    /**
     * Pauses all backfills for the given triggers.
     *
     * @param triggers the triggers.
     * @return the number of backfill paused.
     */
    public int pauseAllBackfillByIds(List<TriggerId> triggers) {
        return backfillsAction(Flux.fromIterable(triggers), BackfillAction.PAUSE);
    }


    /**
     * Resumes all backfills for the given triggers.
     *
     * @param triggers the triggers.
     * @return the number of backfill resumed.
     */
    public int resumeAllBackfillByIds(List<TriggerId> triggers) {
        return backfillsAction(Flux.fromIterable(triggers), BackfillAction.RESUME);
    }

    /**
     * Pauses all backfills for triggers matching the given filters.
     *
     * @param tenant  the tenant identifier.
     * @param filters the filters to match triggers.
     * @throws NotFoundException if no trigger can be found.
     */
    public int pauseAllBackfillMatching(String tenant, List<QueryFilter> filters) {
        return backfillsAction(triggerRepository.find(tenant, filters), BackfillAction.PAUSE);
    }

    /**
     * Resumes all backfills for triggers matching the given filters.
     *
     * @param tenant  the tenant identifier.
     * @param filters the filters to match triggers.
     * @throws NotFoundException if no trigger can be found.
     */
    public int resumeAllBackfillMatching(String tenant, List<QueryFilter> filters) {
        return backfillsAction(triggerRepository.find(tenant, filters), BackfillAction.RESUME);
    }

    /**
     * Create a trigger backfill for the given identifier.
     *
     * @param triggerId the trigger identifier.
     * @throws NotFoundException if no trigger can be found.
     */
    public void createBackfill(TriggerId triggerId, CreateBackfillTrigger.Backfill backfill) {
        getTriggerState(triggerId);  // check if state exists.
        triggerEventQueue.send(new CreateBackfillTrigger(TriggerId.of(triggerId), backfill));
    }

    /**
     * Deletes the trigger backfill for the given identifier.
     *
     * @param triggerId the trigger identifier.
     * @throws NotFoundException if no trigger can be found.
     */
    public void deleteBackfill(TriggerId triggerId) {
        getTriggerState(triggerId);  // check if state exists.
        triggerEventQueue.send(new DeleteBackfillTrigger(TriggerId.of(triggerId)));
    }

    /**
     * Pauses the trigger backfill for the given identifier.
     *
     * @param triggerId the trigger identifier.
     * @throws NotFoundException if no trigger can be found.
     */
    public void setBackfillPaused(TriggerId triggerId, boolean paused) {
        getTriggerState(triggerId);  // check if state exists.
        triggerEventQueue.send(new SetPauseBackfillTrigger(TriggerId.of(triggerId), paused));
    }

    /**
     * Resets a trigger for the given identifier.
     *
     * @param triggerId the trigger identifier.
     * @throws NotFoundException if no trigger can be found.
     */
    public void resetTrigger(final TriggerId triggerId) throws NotFoundException, QueueException {
        getTriggerState(triggerId); // check if state exists.
        this.executionKilledQueue.emit(ExecutionKilledTrigger
            .builder()
            .tenantId(triggerId.getTenantId())
            .namespace(triggerId.getNamespace())
            .flowId(triggerId.getFlowId())
            .triggerId(triggerId.getTriggerId())
            .build()
        );
        triggerEventQueue.send(new ResetTrigger(TriggerId.of(triggerId)));
    }

    /**
     * Unlocks a trigger.
     *
     * @param trigger the identifier of the trigger to unlock
     * @return the updated {@link TriggerState} with the lock removed
     * @throws ConflictException if the trigger is already unlocked
     * @throws NotFoundException if no trigger exists for the given identifiers
     */
    public TriggerState unlockTriggerById(final TriggerId trigger) throws ConflictException, NotFoundException {
        return triggerRepository.findById(trigger)
            .map(state -> {
                if (state.isLocked()) {
                    triggerEventQueue.send(new ResetTrigger(TriggerId.of(trigger)));
                    return state.locked(Clock.systemDefaultZone(), false);
                }
                throw new ConflictException("trigger %s is already unlocked".formatted(trigger));
            }).orElseThrow(() -> new NotFoundException("Trigger %s not found".formatted(trigger)));
    }

    /**
     * Unlocks multiple triggers.
     *
     * @param triggers the list of trigger identifiers unlock
     * @return the number of triggers successfully unlocked.
     */
    public int unlockAllTriggersByIds(List<TriggerId> triggers) {
        return unlockAllTriggersByIds(Flux.fromIterable(triggers));
    }

    /**
     * Unlocks triggers by query.
     *
     * @param tenant  the tenant identifier.
     * @param filters the query filters.
     * @return the number of triggers successfully unlocked.
     * @throws NotFoundException if a trigger can't be found.
     */
    public int unlockAllTriggersMatching(String tenant, List<QueryFilter> filters) throws NotFoundException {
        Flux<TriggerId> flux = triggerRepository.find(tenant, filters).filter(TriggerState::isLocked).map(TriggerId::of);
        return unlockAllTriggersByIds(flux);
    }

    private int unlockAllTriggersByIds(final Flux<TriggerId> triggers) {
        return triggers.map(trigger -> {
                try {
                    unlockTriggerById(trigger);
                    return 1;
                } catch (ConflictException ignored) {
                    // Ignore exception when doing bulk action
                    return 0;
                }
            }).reduce(Integer::sum)
            .blockOptional()
            .orElse(0);
    }

    private int backfillsAction(Flux<? extends TriggerId> triggers, BackfillAction action) {
        return triggers.map(trigger -> {
            try {
                switch (action) {
                    case PAUSE:
                        setBackfillPaused(trigger, true);
                        break;
                    case RESUME:
                        setBackfillPaused(trigger, false);
                        break;
                    case DELETE:
                        deleteBackfill(trigger);
                        break;
                }
                return 1;
            } catch (NotFoundException ignore) {
                // When doing bulk action, we ignore trigger that have no backfills
                return 0;
            }
        }).reduce(Integer::sum).blockOptional().orElse(0);
    }

    private TriggerState getTriggerState(TriggerId triggerId) {
        return triggerRepository.findById(triggerId).orElseThrow(() -> new NotFoundException("Trigger %s not found".formatted(triggerId)));
    }

    private enum BackfillAction {
        PAUSE,
        RESUME,
        DELETE
    }
}
