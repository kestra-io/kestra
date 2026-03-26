package io.kestra.core.models.conditions;

import io.kestra.core.exceptions.InternalException;

/**
 * Conditions of type ScheduleCondition have a special behavior inside the {@link io.kestra.plugin.core.trigger.Schedule} trigger.
 * They are evaluated specifically and would be taken into account when computing the next evaluation date.
 * Such condition must be a condition on a date or there is a risk of infinite evaluation of the trigger (each on sec).
 */
public interface ScheduleCondition {
    boolean test(ConditionContext conditionContext) throws InternalException;
}
