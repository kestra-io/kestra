package io.kestra.scheduler;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.Optional;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import io.kestra.core.events.EventId;
import io.kestra.core.async.AsyncOperation;
import io.kestra.core.async.AsyncOperationProcessedEvent;
import io.kestra.core.async.AsyncOperationService;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKilled;
import io.kestra.core.models.executions.ExecutionKilledTrigger;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowId;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.Backfill;
import io.kestra.core.models.triggers.PollingTriggerInterface;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.models.triggers.TriggerId;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.scheduler.events.CreateBackfillTrigger;
import io.kestra.core.scheduler.events.DeleteBackfillTrigger;
import io.kestra.core.scheduler.events.ResetTrigger;
import io.kestra.core.scheduler.events.SetDisableTrigger;
import io.kestra.core.scheduler.events.SetPauseBackfillTrigger;
import io.kestra.core.scheduler.events.TriggerCreated;
import io.kestra.core.scheduler.events.TriggerDeleted;
import io.kestra.core.scheduler.events.TriggerEvaluated;
import io.kestra.core.scheduler.events.TriggerEvent;
import io.kestra.core.scheduler.events.TriggerExecutionTerminated;
import io.kestra.core.scheduler.events.TriggerFlowRevisionUpdated;
import io.kestra.core.scheduler.events.TriggerReceived;
import io.kestra.core.scheduler.events.TriggerUpdated;
import io.kestra.core.scheduler.events.TriggerWorkerLost;
import io.kestra.core.scheduler.model.TriggerState;
import io.kestra.core.scheduler.model.TriggerType;
import io.kestra.core.scheduler.service.TriggerExecutionPublisher;
import io.kestra.core.scheduler.store.TriggerStateStore;
import io.kestra.core.services.ConditionService;
import io.kestra.core.utils.Logs;
import io.kestra.scheduler.internals.NextEvaluationDate;
import io.kestra.scheduler.stores.FlowMetaStore;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

/**
 * Service responsible for handling {@link TriggerEvent events} and
 * creating, updating, and managing {@link TriggerState trigger states}.
 */
@Singleton
public class TriggerEventHandler {

    private static final Logger LOG = LoggerFactory.getLogger(TriggerEventHandler.class);

    private final TriggerStateStore triggerStateStore;
    private final FlowMetaStore flowStateStore;
    private final TriggerExecutionPublisher triggerExecutionPublisher;
    private final RunContextFactory runContextFactory;
    private final ConditionService conditionService;
    private final BroadcastQueueInterface<ExecutionKilled> executionKilledQueue;
    private final AsyncOperationService asyncOperationService;

    @Inject
    public TriggerEventHandler(@Named("cached") TriggerStateStore triggerStateStore,
        FlowMetaStore flowStateStore,
        TriggerExecutionPublisher triggerExecutionPublisher,
        RunContextFactory runContextFactory,
        ConditionService conditionService,
        BroadcastQueueInterface<ExecutionKilled> executionKilledQueue,
        AsyncOperationService asyncOperationService) {
        this.triggerStateStore = triggerStateStore;
        this.flowStateStore = flowStateStore;
        this.triggerExecutionPublisher = triggerExecutionPublisher;
        this.conditionService = conditionService;
        this.runContextFactory = runContextFactory;
        this.executionKilledQueue = executionKilledQueue;
        this.asyncOperationService = asyncOperationService;
    }

    /**
     * Handles the given {@link TriggerEvent}.
     *
     * @param clock the scheduler clock.
     * @param vNode the trigger vNode.
     * @param event the trigger event.
     */
    public void handle(Clock clock, Integer vNode, TriggerEvent event) {
        LOG.debug("Received event {} for {} at {}", event.type(), event.id(), event.timestamp());
        AsyncOperationProcessedEvent.Outcome outcome = AsyncOperationProcessedEvent.Outcome.SUCCEEDED;
        String error = null;
        try {
            doHandle(clock, vNode, event);
        } catch (RuntimeException e) {
            outcome = AsyncOperationProcessedEvent.Outcome.FAILED;
            error = e.getMessage();
            throw e;
        } finally {
            emitProcessedIfAsync(event, outcome, error);
        }
    }

    private void doHandle(Clock clock, Integer vNode, TriggerEvent event) {
        switch (event) {
            // Events
            case TriggerCreated evt -> onTriggerCreated(clock, evt, vNode);
            case TriggerDeleted evt -> onTriggerDeleted(evt);
            case TriggerUpdated evt -> onTriggerUpdated(clock, evt);
            case TriggerFlowRevisionUpdated evt -> onTriggerFlowRevisionUpdated(evt);
            case TriggerExecutionTerminated evt -> onTriggerExecutionTerminated(clock, evt);
            case TriggerEvaluated evt -> onTriggerEvaluated(clock, evt);
            case TriggerReceived evt -> onTriggerReceived(clock, evt);
            case TriggerWorkerLost evt -> onTriggerWorkerLost(clock, evt);
            // Commands
            case CreateBackfillTrigger evt -> onCreateBackfill(clock, evt);
            case SetPauseBackfillTrigger evt -> onSetPauseBackfillTrigger(clock, evt);
            case SetDisableTrigger evt -> onSetTriggerDisable(clock, evt);
            case DeleteBackfillTrigger evt -> onDeleteBackfillTrigger(clock, evt);
            case ResetTrigger evt -> onResetTrigger(clock, evt);
            default -> throw new IllegalStateException("Unexpected value: " + event);
        }
    }

    private void emitProcessedIfAsync(TriggerEvent message,
                                      AsyncOperationProcessedEvent.Outcome outcome,
                                      String error) {
        if (message instanceof AsyncOperation op) {
            asyncOperationService.emitProcessedIfAsync(op, message.id().getTenantId(), message.uid(), outcome, error);
        }
    }

    /**
     * Handler method for {@link CreateBackfillTrigger}.
     *
     * @param event the event.
     */
    void onCreateBackfill(Clock clock, CreateBackfillTrigger event) {
        findTriggerState(event).ifPresent(state ->
        {
            state = state
                .lastEventId(clock, event.eventId())
                .backfill(
                    clock, Backfill
                        .builder()
                        .start(event.backfill().start())
                        .end(event.backfill().end())
                        .inputs(event.backfill().inputs())
                        .labels(event.backfill().labels())
                        .build()
                );

            Pair<Flow, AbstractTrigger> pair = findTrigger(event, null);
            Flow flow = pair.getLeft();
            AbstractTrigger trigger = pair.getRight();

            if (flow == null || trigger == null) {
                return;
            }

            if (trigger instanceof PollingTriggerInterface) {
                state = state.updateForNextEvaluationDate(clock, nextEvaluationDate(clock, flow, trigger, state.context()));
            }

            triggerStateStore.save(state);
        });
    }

    /**
     * Handler method for {@link SetDisableTrigger}.
     *
     * @param event the event.
     */
    void onDeleteBackfillTrigger(Clock clock, DeleteBackfillTrigger event) {
        findTriggerState(event).ifPresent(state ->
        {
            if (state.getBackfill() != null) {
                ZonedDateTime nextEvaluationDate = state.getBackfill().getPreviousNextExecutionDate();
                state = state
                    .lastEventId(clock, event.eventId())
                    // clear the backfill
                    .backfill(clock, null)
                    // restore the previous next-evaluation date.
                    .updateForNextEvaluationDate(clock, nextEvaluationDate);
                triggerStateStore.save(state);
            }
        });
    }

    /**
     * Handler method for {@link SetPauseBackfillTrigger}.
     *
     * @param event the event.
     */
    void onSetPauseBackfillTrigger(Clock clock, SetPauseBackfillTrigger event) {
        findTriggerState(event).ifPresent(state ->
        {
            if (state.getBackfill() != null) {
                state = state
                    .lastEventId(clock, event.eventId())
                    .backfill(clock, state.getBackfill().toBuilder().paused(event.pause()).build());
                triggerStateStore.save(state);
            }
        });
    }

    /**
     * Handler method for {@link SetDisableTrigger}.
     *
     * @param event the event.
     */
    void onSetTriggerDisable(Clock clock, SetDisableTrigger event) {
        findTriggerState(event).ifPresent(state ->
        {
            boolean wasDisabled = state.isDisabled();
            // Stop the running instance: the disabled flag alone has no effect on a realtime
            // trigger already running on a worker.
            if (!wasDisabled && event.disabled()) {
                maySendExecutionKilled(state);
            }
            state = state
                .lastEventId(clock, event.eventId())
                .disabled(clock, event.disabled());
            // if the trigger is re-enabled, re-compute the next evaluation date
            // this is required to not backfill all missed scheduling after re-enabling trigger.
            if (wasDisabled && !event.disabled()) {
                Pair<Flow, AbstractTrigger> data = findTrigger(event, null);
                if (data.getRight() != null) {
                    state = state.updateForNextEvaluationDate(clock, nextEvaluationDate(clock, data.getLeft(), data.getRight(), state.context()));
                }
            }
            triggerStateStore.save(state);
        });
    }

    /**
     * Handler method for {@link TriggerExecutionTerminated}.
     *
     * @param event the event.
     */
    void onTriggerExecutionTerminated(Clock clock, TriggerExecutionTerminated event) {
        findTriggerState(event).ifPresent(state ->
        {
            // A running realtime trigger emits many executions whose terminations must not release the
            // trigger's lock — unlocking here would resubmit a trigger that is still running on a worker.
            // The only expected termination signal for a realtime trigger is the FAILED execution
            // produced when the trigger could not be started.
            if (TriggerType.REALTIME.equals(state.getType()) && !State.Type.FAILED.equals(event.executionState())) {
                Logs.logTrigger(
                    event.id(),
                    Level.WARN,
                    "Ignoring event '{}' for execution '{}' in state '{}'. Cause: a realtime trigger is only unlocked by a FAILED trigger-creation execution.",
                    event.type(),
                    event.executionId(),
                    event.executionState()
                );
                return;
            }
            triggerStateStore.save(
                state
                    .lastEventId(clock, event.eventId())
                    .locked(clock, false)
                    .updateOnExecutionTerminated(clock, event.executionState())
            );
        });
    }

    /**
     * Handler method for {@link TriggerEvaluated}.
     *
     * @param event the event.
     */
    void onTriggerEvaluated(Clock clock, TriggerEvaluated event) {
        findTriggerState(event).ifPresent(state ->
        {
            Pair<Flow, AbstractTrigger> data = findTrigger(event, null);
            if (data.getLeft() == null) {
                return;
            }

            TriggerState newState = state;
            if (data.getRight() != null) {
                newState = newState.updateForNextEvaluationDate(clock, nextEvaluationDate(clock, data.getLeft(), data.getRight(), state.context()));
            }

            if (event.evaluation() != null) {
                newState = newState.updateOnExecutionCreated(clock, event.evaluation().stateType());
                if (data.getRight() != null && !data.getRight().isAllowConcurrent()) {
                    newState = newState.executionId(clock, event.evaluation().executionId());
                }
            } else {
                // No execution was created (poll matched nothing, or the job was rejected before
                // dispatch): release the lock taken at submission, otherwise the trigger would
                // never be eligible for scheduling again.
                newState = newState
                    .locked(clock, false)
                    .workerId(clock, null);
            }

            newState = newState.lastEventId(clock, event.eventId());
            triggerStateStore.save(newState);

            if (event.evaluation() != null) {
                Execution execution = event.evaluation().toExecution(event.id());
                triggerExecutionPublisher.send(execution);
            }
        });
    }

    /**
     * Handler method for {@link TriggerReceived}.
     *
     * @param event the event.
     */
    void onTriggerReceived(Clock clock, TriggerReceived event) {
        Optional<TriggerState> maybeState = findTriggerState(event);
        if (maybeState.isEmpty()) {
            // The trigger was deleted while its worker job was in flight: kill the instance
            // the worker just started. Only do so when the state is truly missing, not when
            // the event was de-duplicated.
            if (triggerStateStore.findById(event.id()).isEmpty()) {
                sendExecutionKilled(event.id());
            }
            return;
        }
        TriggerState state = maybeState.get();
        // The trigger was disabled while its worker job was in flight: the kill broadcast
        // found no holder at that time, so kill the instance now that a worker reports it.
        if (state.isDisabled()) {
            maySendExecutionKilled(state);
        }
        triggerStateStore.save(
            state
                .lastEventId(clock, event.eventId())
                .workerId(clock, event.workerId())
        );
    }

    /**
     * Handler method for {@link TriggerWorkerLost}.
     * <p>
     * The worker holding the trigger is gone without reporting a result: release the lock so an
     * eligible trigger is resubmitted from the current flow definition, while a disabled one stays off.
     *
     * @param event the event.
     */
    void onTriggerWorkerLost(Clock clock, TriggerWorkerLost event) {
        findTriggerState(event).ifPresent(state ->
        {
            if (state.getWorkerId() != null && !state.getWorkerId().equals(event.workerUid())) {
                // The trigger is already held by another worker.
                return;
            }
            triggerStateStore.save(
                state
                    .lastEventId(clock, event.eventId())
                    .locked(clock, false)
                    .workerId(clock, null)
            );
        });
    }

    /**
     * Handler method for {@link ResetTrigger}.
     *
     * @param event the event.
     */
    void onResetTrigger(Clock clock, ResetTrigger event) {
        findTriggerState(event).ifPresent(state ->
        {
            Pair<Flow, AbstractTrigger> data = findTrigger(event, null);
            state = state
                .lastEventId(clock, event.eventId())
                .reset(clock);
            if (data.getRight() != null) {
                state = state.updateForNextEvaluationDate(clock, nextEvaluationDate(clock, data.getLeft(), data.getRight(), state.context()));
            }
            triggerStateStore.save(state);
        });
    }

    /**
     * Handler method for {@link TriggerUpdated}.
     *
     * @param event the event.
     */
    void onTriggerUpdated(Clock clock, TriggerUpdated event) {
        findTriggerState(event).ifPresent(state ->
        {
            Pair<Flow, AbstractTrigger> data = findTrigger(event, event.revision());
            if (data.getRight() != null) {
                // Kill the running instance so the updated definition is applied: its termination
                // unlocks the state and the scheduler resubmits the trigger with the new configuration.
                maySendExecutionKilled(state);
                state = state
                    .lastEventId(clock, event.eventId())
                    .update(clock, data.getRight())
                    .updateForNextEvaluationDate(clock, nextEvaluationDate(clock, data.getLeft(), data.getRight(), state.context()));
                triggerStateStore.save(state);
            }
        });
    }

    /**
     * Handler method for {@link TriggerFlowRevisionUpdated}.
     * <p>
     * The trigger definition is unchanged; this event only forces the scheduler's
     * flow metadata cache to refresh to the latest revision. No trigger state mutation.
     *
     * @param event the event.
     */
    void onTriggerFlowRevisionUpdated(TriggerFlowRevisionUpdated event) {
        // Side-effect: CachedFlowMetaStore refreshes its cache on newer revision.
        findFlow(event, event.revision());
    }

    /**
     * Handler method for {@link TriggerDeleted}.
     *
     * @param event the event.
     */
    void onTriggerDeleted(TriggerDeleted event) {
        triggerStateStore.findById(event.id()).ifPresent(state ->
        {
            triggerStateStore.delete(event.id());
            maySendExecutionKilled(state);
        });
    }

    /**
     * Kills the running instance of a realtime trigger, if any. A realtime trigger runs for its whole
     * lifetime on a worker and is not affected by state changes (update, disable, delete) until killed.
     */
    private void maySendExecutionKilled(TriggerState state) {
        if (TriggerType.REALTIME.equals(state.getType())) {
            sendExecutionKilled(state);
        }
    }

    private void sendExecutionKilled(TriggerId id) {
        try {
            this.executionKilledQueue.emit(
                ExecutionKilledTrigger
                    .builder()
                    // Trigger kills are not processed by the Executor: emit them directly in the
                    // EXECUTED state, the only state forwarded to the workers.
                    .state(ExecutionKilled.State.EXECUTED)
                    .tenantId(id.getTenantId())
                    .namespace(id.getNamespace())
                    .flowId(id.getFlowId())
                    .triggerId(id.getTriggerId())
                    .build()
            );
        } catch (QueueException e) {
            Logs.logTrigger(id, Level.WARN, "Cannot kill a real-time trigger, it will continue processing until Kestra is restarted. Cause: {}", e.getMessage(), e);
        }
    }

    /**
     * Handler method for {@link TriggerCreated}.
     *
     * @param event the event.
     */
    void onTriggerCreated(Clock clock, TriggerCreated event, Integer vNode) {
        Pair<Flow, AbstractTrigger> data = findTrigger(event, event.revision());
        if (data.getRight() != null) {
            Flow flow = data.getLeft();
            AbstractTrigger trigger = data.getRight();
            TriggerState state = TriggerState
                .of(event.id(), TriggerType.from(trigger), trigger.getStopAfter(), trigger.isDisabled(), vNode)
                .lastEventId(clock, event.eventId());
            state = state.updateForNextEvaluationDate(clock, nextEvaluationDate(clock, flow, trigger, state.context()));
            triggerStateStore.save(state);
        }
    }

    /**
     * Computes the next evaluation date for the given trigger, taking the trigger's conditions into account
     * so handlers don't overwrite a condition-aware date (e.g. {@code DayWeek=SUNDAY}) with the next raw cron tick.
     */
    private ZonedDateTime nextEvaluationDate(Clock clock, Flow flow, AbstractTrigger trigger, TriggerContext triggerContext) {
        RunContext runContext = runContextFactory.of(flow, trigger);
        ConditionContext conditionContext = conditionService.conditionContext(runContext, flow, null);
        return NextEvaluationDate.get(clock, trigger, triggerContext, conditionContext);
    }

    private Pair<Flow, AbstractTrigger> findTrigger(TriggerEvent event, Integer revision) {
        FlowWithSource flow = findFlow(event, revision);
        if (flow == null) {
            return Pair.of(null, null);
        }

        AbstractTrigger trigger = flow.getTriggers().stream()
            .filter(it -> it.getId().equals(event.id().getTriggerId()))
            .findFirst()
            .orElse(null);

        if (trigger == null) {
            Logs.logTrigger(event.id(), Level.WARN, "Cannot process event '{}'. Cause: Trigger not found", event.type());
        }

        return Pair.of(flow, trigger);
    }

    private FlowWithSource findFlow(TriggerEvent event, Integer revision) {
        FlowId flowId = FlowId.of(event.id().getTenantId(), event.id().getNamespace(), event.id().getFlowId(), revision);
        FlowWithSource flow = flowStateStore.find(flowId).orElse(null);
        if (flow == null) {
            Logs.logTrigger(event.id(), Level.WARN, "Cannot process event '{}'. Cause: Flow not found", event.type());
            return null;
        }
        return flow;
    }

    private Optional<TriggerState> findTriggerState(final TriggerEvent event) {
        Optional<TriggerState> state = triggerStateStore.findById(event.id());
        if (state.isEmpty()) {
            Logs.logTrigger(event.id(), Level.WARN, "Cannot process event {}. Cause: Trigger state not found.", event.type());
            return Optional.empty();
        }

        // Ensure event can't be process twice - most queuing systems provide at-least once semantic
        TriggerState current = state.get();
        EventId lastEventId = current.getLastEventId();
        if (lastEventId == null || event.eventId().isNewerThan(lastEventId)) {
            return state;
        }

        // Ignore because it's an older or duplicate event
        Logs.logTrigger(event.id(), Level.WARN, "Skipping event {}. Cause: Event is older than last applied event.", event.type());
        return Optional.empty();
    }
}
