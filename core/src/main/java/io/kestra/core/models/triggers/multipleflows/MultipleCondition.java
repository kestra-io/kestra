package io.kestra.core.models.triggers.multipleflows;

import io.kestra.core.models.conditions.Condition;
import io.kestra.core.models.triggers.SLA;

import java.util.Map;

public interface MultipleCondition {
    String getId();

    SLA getSla();

    Boolean getResetOnSuccess();

    Map<String, Condition> getConditions();
}
