package io.kestra.core.models.conditions;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.kestra.core.exceptions.InternalException;
import io.micronaut.core.annotation.Introspected;

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "type", visible = true, include = JsonTypeInfo.As.EXISTING_PROPERTY)
@Introspected
public interface ScheduleCondition {
    boolean test(ConditionContext conditionContext) throws InternalException;
}
