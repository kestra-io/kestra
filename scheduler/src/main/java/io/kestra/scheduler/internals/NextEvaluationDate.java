package io.kestra.scheduler.internals;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.Optional;

import io.kestra.core.exceptions.InvalidTriggerConfigurationException;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.PollingTriggerInterface;
import io.kestra.core.models.triggers.TriggerContext;

/**
 * Utility class for computing next evaluation date.
 */
public abstract class NextEvaluationDate {

    /**
     * Computes the next evaluation date for the given trigger.
     *
     * @param clock the clock
     * @param trigger the trigger.
     * @param triggerContext the trigger context.
     * @param conditionContext the condition context.
     * @return the next evaluation date.
     * @throws InvalidTriggerConfigurationException if something bad happens while computing next evaluation date.
     */
    public static ZonedDateTime get(Clock clock, AbstractTrigger trigger, TriggerContext triggerContext, ConditionContext conditionContext) throws InvalidTriggerConfigurationException {
        if (!(trigger instanceof PollingTriggerInterface pollingTrigger)) {
            return ZonedDateTime.now(clock); // real-time trigger
        }
        // Seed date=now when the context has neither a prior evaluation date nor a backfill, so
        // Schedule takes its conditions-aware branch instead of falling back to the unconditioned
        // next cron tick (which would ignore DayWeek/DayWeekInMonth etc. at creation time).
        TriggerContext effectiveContext = triggerContext;
        if (effectiveContext == null || (effectiveContext.getDate() == null && effectiveContext.getBackfill() == null)) {
            effectiveContext = (effectiveContext != null ? effectiveContext.toBuilder() : TriggerContext.builder())
                .date(ZonedDateTime.now(clock))
                .build();
        }
        return pollingTrigger.nextEvaluationDate(conditionContext, Optional.of(effectiveContext));
    }

    /**
     * Computes the next evaluation date for the given trigger.
     * <p>
     * <b>Warning:</b> this overload ignores trigger conditions and returns the next raw cron tick.
     * Prefer {@link #get(Clock, AbstractTrigger, TriggerContext, ConditionContext)} whenever a
     * {@link ConditionContext} can be built — only use this overload from paths where one is
     * genuinely unavailable (e.g. error recovery after an exception).
     *
     * @param clock the clock
     * @param trigger the trigger.
     * @return the next evaluation date.
     * @throws InvalidTriggerConfigurationException if something bad happens while computing next evaluation date.
     */
    public static ZonedDateTime get(Clock clock, AbstractTrigger trigger) throws InvalidTriggerConfigurationException {
        ZonedDateTime nextExecutionDate;
        if (trigger instanceof PollingTriggerInterface pollingTrigger) {
            nextExecutionDate = pollingTrigger.nextEvaluationDate();
        } else {
            nextExecutionDate = ZonedDateTime.now(clock); // real-time trigger
        }

        return nextExecutionDate;
    }
}
