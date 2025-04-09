package io.kestra.core.models.triggers.multipleflows;

import io.kestra.core.models.flows.FlowId;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.triggers.TimeWindow;
import org.apache.commons.lang3.tuple.Pair;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.kestra.core.models.triggers.TimeWindow.Type.DURATION_WINDOW;

public interface MultipleConditionStorageInterface {
    Optional<MultipleConditionWindow> get(FlowId flow, String conditionId);

    List<MultipleConditionWindow> expired(String tenantId);

    default MultipleConditionWindow getOrCreate(FlowId flow, MultipleCondition multipleCondition, Map<String, Object> outputs) {
        ZonedDateTime now = ZonedDateTime.now().withNano(0);
        TimeWindow timeWindow = multipleCondition.getTimeWindow() != null ? multipleCondition.getTimeWindow() : TimeWindow.builder().build();

        TimeWindow.Type type = timeWindow.getType() != null ? timeWindow.getType() : DURATION_WINDOW;
        var startAndEnd = switch (type) {
            case DURATION_WINDOW -> {
                Duration window = timeWindow.getWindow() == null ? Duration.ofDays(1) : timeWindow.getWindow();
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

                ZonedDateTime startWindow = timeWindow.getWindowAdvance() == null ? now : now.plus(timeWindow.getWindowAdvance()).truncatedTo(ChronoUnit.MILLIS);
                yield Pair.of(
                    startWindow,
                    startWindow.plus(window).minus(Duration.ofMillis(1)).truncatedTo(ChronoUnit.MILLIS)
                );
            }
            case SLIDING_WINDOW -> Pair.of(
                now.truncatedTo(ChronoUnit.MILLIS),
                now.truncatedTo(ChronoUnit.MILLIS).plus(timeWindow.getWindow() == null ? Duration.ofDays(1) : timeWindow.getWindow())
            );
            case DAILY_TIME_WINDOW -> Pair.of(
                now.truncatedTo(ChronoUnit.DAYS).plusSeconds(timeWindow.getStartTime().toSecondOfDay()),
                now.truncatedTo(ChronoUnit.DAYS).plusSeconds(timeWindow.getEndTime().toSecondOfDay())
            );
            case DAILY_TIME_DEADLINE -> Pair.of(
                now.truncatedTo(ChronoUnit.DAYS),
                now.truncatedTo(ChronoUnit.DAYS).plusSeconds(timeWindow.getDeadline().toSecondOfDay())
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
                .outputs(outputs)
                .build()
            );
    }

    void save(List<MultipleConditionWindow> multipleConditionWindows);

    void delete(MultipleConditionWindow multipleConditionWindow);
}
