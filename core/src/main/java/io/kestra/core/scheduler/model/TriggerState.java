package io.kestra.core.scheduler.model;

import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.kestra.core.events.EventId;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.FlowId;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.Backfill;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.models.triggers.TriggerId;
import io.kestra.core.scheduler.SchedulerClock;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * Immutable class representing the state of a trigger.
 */
@Getter
@EqualsAndHashCode
@AllArgsConstructor
@Builder
public final class TriggerState implements TriggerId {
    private final String tenantId;
    private final String namespace;
    private final String flowId;
    private final String triggerId;
    private final Instant updatedAt;
    private final Instant evaluatedAt;
    private final Instant nextEvaluationDate;
    private final Backfill backfill;
    private final List<State.Type> stopAfter;
    private final boolean disabled;
    private final int vnode;
    private final boolean locked;
    private final String workerId;
    private final TriggerType type;
    // the last-event id that mutate this state. 
    private final EventId lastEventId;

    @JsonProperty
    public Long getNextEvaluationEpoch() {
        return Optional.ofNullable(nextEvaluationDate)
            .map(Instant::toEpochMilli)
            .orElse(null);
    }

    public TriggerContext context() {
        return TriggerContext.builder()
            .tenantId(tenantId)
            .namespace(namespace)
            .flowId(flowId)
            .triggerId(triggerId)
            .date(toZonedDateTime(evaluatedAt))
            .nextExecutionDate(toZonedDateTime(nextEvaluationDate))
            .stopAfter(stopAfter)
            .disabled(disabled)
            .build();
    }

    /**
     * Factory method for constructing a new {@link TriggerState}.
     *
     * @return a new {@link TriggerState}
     */
    public static TriggerState of(FlowId flowId, AbstractTrigger trigger, Integer vnode) {
        return of(TriggerId.of(flowId, trigger), TriggerType.from(trigger), trigger.getStopAfter(), trigger.isDisabled(), vnode);
    }

    /**
     * Factory method for constructing a new {@link TriggerState}.
     *
     * @return a new {@link TriggerState}
     */
    public static TriggerState of(TriggerId id, TriggerType type, List<State.Type> stopAfter, Boolean disabled, Integer vnode) {
        return new TriggerState(
            id.getTenantId(),
            id.getNamespace(),
            id.getFlowId(),
            id.getTriggerId(),
            Instant.now(),
            null,
            null,
            null,
            stopAfter,
            disabled,
            vnode,
            false,
            null,
            type,
            null
        );
    }

    /**
     * Updates this trigger state based on the trigger.
     *
     * @param clock the scheduler clock.
     * @return a new {@link TriggerState}
     */
    public TriggerState update(Clock clock, AbstractTrigger trigger) {
        return update(clock)
            .stopAfter(trigger.getStopAfter())
            .disabled(trigger.isDisabled())
            .type(TriggerType.from(trigger))
            .build();
    }

    /**
     * Updates the vNode of this trigger state.
     *
     * @param clock the scheduler clock.
     * @return a new {@link TriggerState}
     */
    public TriggerState vNode(final Clock clock, final int vnode) {
        return update(clock).vnode(vnode).build();
    }

    /**
     * Updates this trigger state with the given {@literal evaluatedAt}.
     *
     * @param clock the scheduler clock.
     * @return a new {@link TriggerState}
     */
    public TriggerState evaluatedAt(final Clock clock, final ZonedDateTime evaluatedAt) {
        return evaluatedAt(clock, evaluatedAt.toInstant());
    }

    /**
     * Updates this trigger state with the given {@literal evaluatedAt}.
     *
     * @param clock the scheduler clock.
     * @return a new {@link TriggerState}
     */
    public TriggerState evaluatedAt(final Clock clock, final Instant evaluatedAt) {
        return update(clock).evaluatedAt(evaluatedAt).build();
    }

    /**
     * Disabled this trigger state.
     *
     * @param clock the scheduler clock.
     * @return a new {@link TriggerState}
     */
    public TriggerState disabled(final Clock clock, boolean disabled) {
        return update(clock).disabled(disabled).build();
    }

    /**
     * Attaches a worker-id to this trigger state.
     *
     * @param clock the scheduler clock.
     * @return a new {@link TriggerState}
     */
    public TriggerState workerId(final Clock clock, String workerId) {
        return update(clock).workerId(workerId).build();
    }

    /**
     * Locks this trigger state.
     *
     * @param clock the scheduler clock.
     * @return a new {@link TriggerState}
     */
    public TriggerState locked(final Clock clock, boolean locked) {
        return update(clock).locked(locked).build();
    }

    /**
     * Updates this trigger state for the given {@code nextEvaluationDate}.
     *
     * @param clock the scheduler clock.
     * @param nextEvaluationDate the next evaluation date.
     * @return a new {@link TriggerState}
     */
    public TriggerState updateForNextEvaluationDate(final Clock clock, final ZonedDateTime nextEvaluationDate) {
        return updateForNextEvaluationDate(clock, nextEvaluationDate.toInstant());
    }

    /**
     * Updates this trigger state for the given {@code nextEvaluationDate}.
     *
     * @param clock the scheduler clock.
     * @param nextEvaluationDate the next evaluation date.
     * @return a new {@link TriggerState}
     */
    public TriggerState updateForNextEvaluationDate(final Clock clock, final Instant nextEvaluationDate) {
        return update(clock)
            .nextEvaluationDate(nextEvaluationDate)
            .backfill(getBackFillForNextEvaluationDate(nextEvaluationDate))
            .build();
    }

    /**
     * Updates this trigger state for the given {@link Backfill}.
     *
     * @param clock the scheduler clock.
     * @param backfill the backfill.
     * @return a new {@link TriggerState}
     */
    public TriggerState backfill(final Clock clock, Backfill backfill) {
        if (backfill != null) {
            backfill = backfill
                .toBuilder()
                .end(backfill.getEnd() != null ? backfill.getEnd() : ZonedDateTime.now(clock))
                .currentDate(backfill.getStart())
                .previousNextExecutionDate(toZonedDateTime(nextEvaluationDate))
                .build();
        }
        return update(clock).backfill(backfill).build();
    }

    /**
     * Updates this trigger state for the given {@link Execution}.
     *
     * @param clock the scheduler clock.
     * @param execution the execution.
     * @return a new {@link TriggerState}
     */
    public TriggerState updateForExecution(final Clock clock, final Execution execution) {
        return updateForExecutionState(clock, execution.getState().getCurrent());
    }

    /**
     * Updates this trigger state for the given executions.
     *
     * @param clock the scheduler clock.
     * @param state the execution state.
     * @return a new {@link TriggerState}
     */
    public TriggerState updateForExecutionState(final Clock clock, final State.Type state) {
        // switch disabled automatically if the executionEndState is one of the stopAfter states
        boolean disabled = getStopAfter() != null ? getStopAfter().contains(state) : isDisabled();

        return update(clock).disabled(disabled).build();
    }

    /**
     * Resets this trigger state.
     *
     * @param clock the scheduler clock.
     * @return a new {@link TriggerState}
     */
    public TriggerState reset(Clock clock) {
        return update(clock)
            .nextEvaluationDate(null)
            .locked(false)
            .workerId(null)
            .build();
    }

    /**
     * Sets the tenant of this trigger state.
     *
     * @return a new {@link TriggerState}
     */
    public TriggerState tenantId(String tenantId) {
        return update(null)
            .tenantId(tenantId)
            .build();
    }

    /**
     * Sets the tenant of this trigger state.
     *
     * @return a new {@link TriggerState}
     */
    public TriggerState lastEventId(Clock clock, EventId eventId) {
        return update(clock)
            .lastEventId(eventId)
            .build();
    }

    private Backfill getBackFillForNextEvaluationDate(final Instant nextEvaluationDate) {
        final ZonedDateTime localNextEvaluationDate = toZonedDateTime(nextEvaluationDate);
        if (backfill != null && !backfill.getPaused()) {
            if (localNextEvaluationDate.isAfter(backfill.getEnd())) {
                return null;
            } else {
                return backfill.toBuilder().currentDate(localNextEvaluationDate).build();
            }
        }
        return backfill;
    }

    private static ZonedDateTime toZonedDateTime(Instant instant) {
        return Optional.ofNullable(instant).map(it -> it.atZone(SchedulerClock.getClock().getZone())).orElse(null);
    }

    private TriggerStateBuilder update(final Clock clock) {
        return TriggerState.builder()
            .tenantId(tenantId)
            .namespace(namespace)
            .flowId(flowId)
            .triggerId(triggerId)
            .updatedAt(clock != null ? clock.instant() : updatedAt)
            .evaluatedAt(evaluatedAt)
            .nextEvaluationDate(nextEvaluationDate)
            .backfill(backfill)
            .stopAfter(stopAfter)
            .locked(locked)
            .workerId(workerId)
            .vnode(vnode)
            .disabled(disabled)
            .type(type)
            .lastEventId(lastEventId);
    }

    // Lombok hack to properly generate Javadoc
    public static class TriggerStateBuilder {
    }
}
