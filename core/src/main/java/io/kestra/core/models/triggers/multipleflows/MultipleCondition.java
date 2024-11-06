package io.kestra.core.models.triggers.multipleflows;

import io.kestra.core.models.conditions.Condition;
import io.kestra.core.models.triggers.TimeSLA;

import java.util.Map;

public interface MultipleCondition {
    String getId();

    TimeSLA getTimeSLA();

    Boolean getResetOnSuccess();

    Map<String, Condition> getConditions();
}
