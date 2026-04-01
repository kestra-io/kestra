package io.kestra.scheduler;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.Optional;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import io.kestra.core.events.EventId;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKilled;
import io.kestra.core.models.executions.ExecutionKilledTrigger;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowId;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.Backfill;
import io.kestra.core.models.triggers.PollingTriggerInterface;
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
import io.kestra.core.scheduler.events.TriggerReceived;
import io.kestra.core.scheduler.events.TriggerUpdated;
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

    @Inject
    public TriggerEventHandler(@Named("cached") TriggerStateStore triggerStateStore,
        FlowMetaStore flowStateStore,
        TriggerExecutionPublisher triggerExecutionPublisher,
        RunContextFactory runContextFactory,
        ConditionService conditionService,
        BroadcastQueueInterface<ExecutionKilled> executionKilledQueue) {
        this.triggerStateStore = triggerStateStore;
        this.flowStateStore = flowStateStore;
        this.triggerExecutionPublisher = triggerExecutionPublisher;
        this.conditionService = conditionService;
        this.runContextFactory = runContextFactory;
        this.executionKilledQueue = executionKilledQueue;
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
        switch (event) {
            // Events
            case TriggerCreated evt -> onTriggerCreated(clock, evt, vNode);
            case TriggerDeleted evt -> onTriggerDeleted(evt);
            case TriggerUpdated evt -> onTriggerUpdated(clock, evt);
            case TriggerExecutionTerminated evt -> onTriggerExecutionTerminated(clock, evt);
            case TriggerEvaluated evt -> onTriggerEvaluated(clock, evt);
            case TriggerReceived evt -> onTriggerReceived(clock, evt);
            // Commands
            case CreateBackfillTrigger evt -> onCreateBackfill(clock, evt);
            case SetPauseBackfillTrigger evt -> onSetPauseBackfillTrigger(clock, evt);
            case SetDisableTrigger evt -> onSetTriggerDisable(clock, evt);
            case DeleteBackfillTrigger evt -> onDeleteBackfillTrigger(clock, evt);
            case ResetTrigger evt -> onResetTrigger(clock, evt);
            default -> throw new IllegalStateException("Unexpected value: " + event);
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

            RunContext runContext = runContextFactory.of(flow, trigger);
            ConditionContext conditionContext = conditionService.conditionContext(runContext, flow, null);

            if (trigger instanceof PollingTriggerInterface pollingTriggerInterface) {
                ZonedDateTime nextEvaluationDate = pollingTriggerInterface.nextEvaluationDate(conditionContext, Optional.of(state.context()));
                state = state.updateForNextEvaluationDate(clock, nextEvaluationDate);
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
            state = state
                .lastEventId(clock, event.eventId())
                .disabled(clock, event.disabled());
            // if the trigger is re-enabled, re-compute the next evaluation date
            // this is required to not backfill all missed scheduling after re-enabling trigger.
            if (wasDisabled && !event.disabled()) {
                Pair<Flow, AbstractTrigger> data = findTrigger(event, null);
                if (data.getRight() != null) {
                    state = state.updateForNextEvaluationDate(clock, NextEvaluationDate.get(clock, data.getRight()));
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
            triggerStateStore.save(
                state
                    .lastEventId(clock, event.eventId())
                    .locked(clock, false)
                    .updateForExecutionState(clock, event.executionState())
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
                newState = newState.updateForNextEvaluationDate(clock, NextEvaluationDate.get(clock, data.getRight()));
            }

            if (event.execution() != null) {
                newState = newState.updateForExecution(clock, event.execution());
            }

            newState = newState.lastEventId(clock, event.eventId());
            triggerStateStore.save(newState);

            if (event.execution() != null) {
                Execution execution = event.execution().withTenantId(state.getTenantId());
                triggerExecutionPublisher.send(execution);
            }
        });
    }

    /**
     * Handler method for {@link ResetTrigger}.
     *
     * @param event the event.
     */
    void onTriggerReceived(Clock clock, TriggerReceived event) {
        findTriggerState(event).ifPresent(state ->
        {
            state = state
                .lastEventId(clock, event.eventId())
                .workerId(clock, event.workerId());
            triggerStateStore.save(state);
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
            state = state
                .lastEventId(clock, event.eventId())
                .reset(clock);
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
                state = state
                    .lastEventId(clock, event.eventId())
                    .update(clock, data.getRight());
                triggerStateStore.save(state);
            }
        });
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
            maySendExecutionKilled(event, state);
        });
    }

    private void maySendExecutionKilled(TriggerDeleted event, TriggerState state) {
        if (TriggerType.REALTIME.equals(state.getType())) {
            try {
                this.executionKilledQueue.emit(
                    ExecutionKilledTrigger
                        .builder()
                        .tenantId(state.getTenantId())
                        .namespace(state.getNamespace())
                        .flowId(state.getFlowId())
                        .triggerId(state.getTriggerId())
                        .build()
                );
            } catch (QueueException e) {
                Logs.logTrigger(event.id(), Level.WARN, "Cannot kill a real-time trigger, it will continue processing until Kestra is restarted. Cause: {}", e.getMessage(), e);
            }
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
            TriggerState state = TriggerState
                .of(event.id(), TriggerType.from(data.getRight()), data.getRight().getStopAfter(), data.getRight().isDisabled(), vNode)
                .lastEventId(clock, event.eventId());
            triggerStateStore.save(state);
        }
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
