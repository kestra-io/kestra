package io.kestra.core.models.triggers.multipleflows;

import io.kestra.core.models.flows.Flow;
import org.apache.commons.lang3.tuple.Pair;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

public interface MultipleConditionStorageInterface {
    Optional<MultipleConditionWindow> get(Flow flow, String conditionId);

    List<MultipleConditionWindow> expired(String tenantId);

    default MultipleConditionWindow getOrCreate(Flow flow, MultipleCondition multipleCondition) {
        ZonedDateTime now = ZonedDateTime.now().withNano(0);
        MultipleCondition.SLA sla = multipleCondition.getSla();

        var startAndEnd = switch (sla.getType()) {
            case DURATION_WINDOW -> {
                Duration window = sla.getWindow() == null ? Duration.ofDays(1) : sla.getWindow();
                if (window.toDays() > 0) {
                    now = now.withHour(0);
                }

                if (window.toHours() > 0) {
                    now = now.withMinute(0);
                }

                if (window.toMinutes() > 0) {
                    now = now.withSecond(0)
                        .withMinute(0)
                        .plusMinutes(window.toMinutes() * (now.getMinute() / window.toMinutes()));
                }

                ZonedDateTime startWindow = sla.getWindowAdvance() == null ? now : now.plus(sla.getWindowAdvance()).truncatedTo(ChronoUnit.MILLIS);
                yield Pair.of(
                    startWindow,
                    startWindow.plus(window).minus(Duration.ofMillis(1)).truncatedTo(ChronoUnit.MILLIS)
                );
            }
            case SLIDING_WINDOW -> Pair.of(
                now.truncatedTo(ChronoUnit.MILLIS),
                now.truncatedTo(ChronoUnit.MILLIS).plus(sla.getWindow() == null ? Duration.ofDays(1) : sla.getWindow())
            );
            case DAILY_TIME_WINDOW -> Pair.of(
                now.truncatedTo(ChronoUnit.DAYS).plusSeconds(sla.getStartTime().toSecondOfDay()),
                now.truncatedTo(ChronoUnit.DAYS).plusSeconds(sla.getEndTime().toSecondOfDay())
            );
            case DAILY_TIME_DEADLINE -> Pair.of(
                now.truncatedTo(ChronoUnit.DAYS),
                now.truncatedTo(ChronoUnit.DAYS).plusSeconds(sla.getDeadline().toSecondOfDay())
            );
        };

        return this.get(flow, multipleCondition.getId())
            .filter(m -> m.isValid(ZonedDateTime.now()))
            .orElseGet(() -> MultipleConditionWindow.builder()
                .namespace(flow.getNamespace())
                .flowId(flow.getId())
                .tenantId(flow.getTenantId())
                .conditionId(multipleCondition.getId())
                .start(startAndEnd.getLeft())
                .end(startAndEnd.getRight())
                .results(new HashMap<>())
                .build()
            );
    }

    void save(List<MultipleConditionWindow> multipleConditionWindows);

    void delete(MultipleConditionWindow multipleConditionWindow);
}
