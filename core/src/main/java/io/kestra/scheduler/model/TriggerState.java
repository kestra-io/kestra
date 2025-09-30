package io.kestra.scheduler.model;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.FlowId;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.Backfill;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.models.triggers.TriggerId;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Immutable class representing the state of a trigger.
 */
@Getter
@EqualsAndHashCode
@AllArgsConstructor
public final class TriggerState implements TriggerId {
    private final String tenantId;
    private final String namespace;
    private final String flowId;
    private final String triggerId;
    private final Instant updatedAt;
    private final ZonedDateTime evaluatedAt;
    private final ZonedDateTime nextEvaluationDate;
    private final Backfill backfill;
    private final List<State.Type> stopAfter;
    private final Boolean disabled;
    private final Integer vnode;
    private final Boolean locked;
    
    public TriggerContext context() {
        return TriggerContext.builder()
            .tenantId(tenantId)
            .namespace(namespace)
            .flowId(flowId)
            .triggerId(triggerId)
            .date(evaluatedAt)
            .stopAfter(stopAfter)
            .disabled(disabled)
            .nextExecutionDate(nextEvaluationDate)
            .build();
    }
    
    /**
     * Factory method for constructing a new {@link TriggerState}.
     *
     * @return a new {@link TriggerState}
     */
    public static TriggerState of(FlowId flowId, AbstractTrigger trigger, Integer vnode) {
        return of(TriggerId.of(flowId, trigger), trigger.getStopAfter(), trigger.isDisabled(), vnode);
    }
    
    /**
     * Factory method for constructing a new {@link TriggerState}.
     *
     * @return a new {@link TriggerState}
     */
    public static TriggerState of(TriggerId id, List<State.Type> stopAfter, Boolean disabled, Integer vnode) {
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
            false
        );
    }
    
    public TriggerState update(Clock clock, AbstractTrigger trigger) {
        return new TriggerState(
            tenantId,
            namespace,
            flowId,
            triggerId,
            clock.instant(),
            evaluatedAt,
            nextEvaluationDate,
            backfill,
            trigger.getStopAfter(),
            trigger.isDisabled(),
            vnode,
            locked
        );
    }
    
    /**
     * Updates the vNode of this trigger state.
     *
     * @param clock the scheduler clock.
     * @return a new {@link TriggerState}
     */
    public TriggerState vNode(final Clock clock, final int vnode) {
        return new TriggerState(
            tenantId,
            namespace,
            flowId,
            triggerId,
            clock.instant(),
            evaluatedAt,
            nextEvaluationDate,
            backfill,
            stopAfter,
            disabled,
            vnode,
            locked
        );
    }
    
    /**
     * Updates this trigger state with the given {@literal evaluatedAt}.
     *
     * @param clock the scheduler clock.
     * @return a new {@link TriggerState}
     */
    public TriggerState evaluatedAt(final Clock clock, final ZonedDateTime evaluatedAt) {
        return new TriggerState(
            tenantId,
            namespace,
            flowId,
            triggerId,
            clock.instant(),
            evaluatedAt,
            nextEvaluationDate,
            backfill,
            stopAfter,
            disabled,
            vnode,
            locked
        );
    }
    
    public TriggerState disabled(final Clock clock, boolean disabled) {
        return new TriggerState(
            tenantId,
            namespace,
            flowId,
            triggerId,
            clock.instant(),
            evaluatedAt,
            nextEvaluationDate,
            backfill,
            stopAfter,
            disabled,
            vnode,
            locked
        );
    }
    
    public TriggerState locked(final Clock clock, boolean locked) {
        return new TriggerState(
            tenantId,
            namespace,
            flowId,
            triggerId,
            clock.instant(),
            evaluatedAt,
            nextEvaluationDate,
            backfill,
            stopAfter,
            disabled,
            vnode,
            locked
        );
    }
    
    /**
     * Updates this trigger state for the given  {@code nextEvaluationDate}.
     *
     * @param clock              the scheduler clock.
     * @param nextEvaluationDate the next evaluation date.
     * @return a new {@link TriggerState}
     */
    public TriggerState updateForNextEvaluationDate(final Clock clock, final ZonedDateTime nextEvaluationDate) {
        return new TriggerState(
            tenantId,
            namespace,
            flowId,
            triggerId,
            clock.instant(),
            evaluatedAt,
            nextEvaluationDate,
            getBackFillForNextEvaluationDate(nextEvaluationDate),
            stopAfter,
            disabled,
            vnode,
            locked
        );
    }
    
    /**
     * Updates this trigger state for the given {@link Backfill}.
     *
     * @param clock     the scheduler clock.
     * @param backfill  the backfill.
     * @return a new {@link TriggerState}
     */
    public TriggerState backfill(final Clock clock, Backfill backfill) {
        if (backfill != null) {
            backfill = backfill
                .toBuilder()
                .end(backfill.getEnd() != null ? backfill.getEnd() : ZonedDateTime.now(clock))
                .currentDate(backfill.getStart())
                .previousNextExecutionDate(nextEvaluationDate)
                .build();
        }
        return new TriggerState(
            tenantId,
            namespace,
            flowId,
            triggerId,
            clock.instant(),
            evaluatedAt,
            nextEvaluationDate,
            backfill,
            stopAfter,
            disabled,
            vnode,
            locked
        );
    }
    
    /**
     * Updates this trigger state for the given {@link Execution}.
     *
     * @param clock     the scheduler clock.
     * @param execution the execution.
     * @return a new {@link TriggerState}
     */
    public TriggerState updateForExecution(final Clock clock, final Execution execution) {
        return updateForExecutionState(clock, execution.getState().getCurrent());
    }
    
    /**
     * Updates this trigger state for the given executions.
     *
     * @param clock       the scheduler clock.
     * @param state       the execution state.
     * @return a new {@link TriggerState}
     */
    public TriggerState updateForExecutionState(final Clock clock, final State.Type state) {
        // switch disabled automatically if the executionEndState is one of the stopAfter states
        Boolean disabled = getStopAfter() != null ? getStopAfter().contains(state) : getDisabled();
        
        return new TriggerState(
            tenantId,
            namespace,
            flowId,
            triggerId,
            clock.instant(),
            evaluatedAt,
            nextEvaluationDate,
            backfill,
            stopAfter,
            disabled,
            vnode,
            locked
        );
    }
    
    private Backfill getBackFillForNextEvaluationDate(final ZonedDateTime nextEvaluationDate) {
        if (backfill != null && !backfill.getPaused()) {
            if (nextEvaluationDate.isAfter(backfill.getEnd())) {
                return null;
            } else {
                return backfill.toBuilder().currentDate(nextEvaluationDate).build();
            }
        }
        return backfill;
    }
    
    public TriggerState reset(Clock clock) {
        return new TriggerState(
            tenantId,
            namespace,
            flowId,
            triggerId,
            clock.instant(),
            evaluatedAt,
            null,
            backfill,
            stopAfter,
            disabled,
            vnode,
            false
        );
    }
}
