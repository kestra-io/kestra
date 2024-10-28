package io.kestra.core.models.triggers.multipleflows;

import io.kestra.core.models.conditions.Condition;

import java.time.Duration;
import java.util.Map;

public interface MultipleCondition {
    String getId();

    Duration getWindow();

    Duration getWindowAdvance();

    Map<String, Condition> getConditions();
}
