package io.kestra.core.models.conditions;

import io.kestra.core.exceptions.InternalException;


public interface ScheduleCondition {
    boolean test(ConditionContext conditionContext) throws InternalException;
}
