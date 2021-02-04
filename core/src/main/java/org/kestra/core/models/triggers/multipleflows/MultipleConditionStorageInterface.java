package org.kestra.core.models.triggers.multipleflows;

import org.kestra.core.models.conditions.types.MultipleCondition;
import org.kestra.core.models.flows.Flow;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

public interface MultipleConditionStorageInterface {
    Optional<MultipleConditionWindow> get(Flow flow, String conditionId);

    List<MultipleConditionWindow> expired();

    default MultipleConditionWindow getOrCreate(Flow flow, MultipleCondition multipleCondition) {
        ZonedDateTime now = ZonedDateTime.now()
            .withNano(0);

        if (multipleCondition.getWindow().toDays() > 0) {
            now = now.withHour(0);
        }

        if (multipleCondition.getWindow().toHours() > 0) {
            now = now.withMinute(0);
        }

        if (multipleCondition.getWindow().toMinutes() > 0) {
            now = now.withSecond(0)
                .withMinute(0)
                .plusMinutes(multipleCondition.getWindow().toMinutes() * (now.getMinute() / multipleCondition.getWindow().toMinutes()));
        }

        ZonedDateTime start = now.plus(multipleCondition.getWindowAdvance());
        ZonedDateTime end = start.plus(multipleCondition.getWindow()).minus(Duration.ofNanos(1));

        return this.get(flow, multipleCondition.getId())
            .filter(m -> m.isValid(ZonedDateTime.now()))
            .orElseGet(() -> MultipleConditionWindow.builder()
                .namespace(flow.getNamespace())
                .flowId(flow.getId())
                .conditionId(multipleCondition.getId())
                .start(start)
                .end(end)
                .results(new HashMap<>())
                .build()
            );
    }
}
