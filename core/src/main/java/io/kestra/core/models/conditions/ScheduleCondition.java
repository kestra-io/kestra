package io.kestra.core.models.conditions;

import io.kestra.core.exceptions.InternalException;

/**
 * Conditions of type ScheduleCondition have a special behavior inside the {@link io.kestra.plugin.core.trigger.Schedule} trigger.
 * They are evaluated specifically and would be taken into account when computing the next evaluation date.
 * Only conditions based on date should be marked as ScheduleCondition.
 */
public interface ScheduleCondition {
    boolean test(ConditionContext conditionContext) throws InternalException;
}
