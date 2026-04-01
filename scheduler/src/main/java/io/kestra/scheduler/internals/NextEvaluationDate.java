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
        ZonedDateTime nextExecutionDate;

        if (trigger instanceof PollingTriggerInterface pollingTrigger) {
            nextExecutionDate = pollingTrigger.nextEvaluationDate(conditionContext, Optional.ofNullable(triggerContext));
        } else {
            nextExecutionDate = ZonedDateTime.now(clock); // real-time trigger
        }
        return nextExecutionDate;
    }

    /**
     * Computes the next evaluation date for the given trigger.
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
