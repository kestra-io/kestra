package io.kestra.core.models.conditions;

import io.kestra.core.exceptions.InternalException;

/**
 * A ScheduleCondition is a special type of condition that will be used by the Schedule trigger when computing its next date.
 * Such condition must be a condition on a date or there is a risk of infinite evaluation of the trigger (each on sec).
 */
public interface ScheduleCondition {
    boolean test(ConditionContext conditionContext) throws InternalException;
}
