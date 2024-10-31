package io.kestra.core.models.triggers.multipleflows;

import io.kestra.core.models.conditions.Condition;
import lombok.Builder;
import lombok.Getter;

import java.time.Duration;
import java.time.LocalTime;
import java.util.Map;

public interface MultipleCondition {
    String getId();

    LatencySLA getLatencySLA();

    Duration getWindow();

    Duration getWindowAdvance();

    Map<String, Condition> getConditions();

    @Getter
    @Builder
    class LatencySLA {
        private LocalTime deadline;
    }
}
