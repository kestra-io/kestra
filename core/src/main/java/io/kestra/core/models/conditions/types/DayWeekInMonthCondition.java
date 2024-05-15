package io.kestra.core.models.conditions.types;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.conditions.Condition;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.conditions.ScheduleCondition;
import io.kestra.core.utils.DateUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import jakarta.validation.constraints.NotNull;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Condition to allow events on weekdays relative to current month (first, last, ...)"
)
@Plugin(
    examples = {
        @Example(
            full = true,
            code = {
                "- conditions:",
                "    - type: io.kestra.core.models.conditions.types.DayWeekInMonthCondition",
                "      dayOfWeek: MONDAY",
                "      dayInMonth: FIRST",
            }
        )
    },
    aliases = "io.kestra.core.models.conditions.types.DayWeekInMonthCondition"
)
public class DayWeekInMonthCondition extends Condition implements ScheduleCondition {
    @NotNull
    @Schema(
        title = "The date to test.",
        description = "Can be any variable or any valid ISO 8601 datetime. By default, it will use the trigger date."
    )
    @Builder.Default
    @PluginProperty(dynamic = true)
    private final String date = "{{ trigger.date }}";

    @NotNull
    @Schema(title = "The day of week.")
    @PluginProperty
    private DayOfWeek dayOfWeek;

    @NotNull
    @Schema(title = "Are you looking for the first or the last day in the month?")
    @PluginProperty
    private DayWeekInMonthCondition.DayInMonth dayInMonth;

    @Override
    public boolean test(ConditionContext conditionContext) throws InternalException {
        String render = conditionContext.getRunContext().render(date, conditionContext.getVariables());
        LocalDate currentDate = DateUtils.parseLocalDate(render);
        LocalDate computed;

        if (dayInMonth.equals(DayInMonth.FIRST)) {
            computed = currentDate.with(TemporalAdjusters.firstInMonth(dayOfWeek));
        } else if (dayInMonth.equals(DayInMonth.LAST)) {
            computed = currentDate.with(TemporalAdjusters.lastInMonth(dayOfWeek));
        } else if (dayInMonth.equals(DayInMonth.SECOND)) {
            computed = currentDate.with(TemporalAdjusters.firstInMonth(dayOfWeek)).with(TemporalAdjusters.next(dayOfWeek));
        } else if (dayInMonth.equals(DayInMonth.THIRD)) {
            computed = currentDate.with(TemporalAdjusters.firstInMonth(dayOfWeek)).with(TemporalAdjusters.next(dayOfWeek)).with(TemporalAdjusters.next(dayOfWeek));
        } else if (dayInMonth.equals(DayInMonth.FOURTH)) {
            computed = currentDate.with(TemporalAdjusters.firstInMonth(dayOfWeek)).with(TemporalAdjusters.next(dayOfWeek)).with(TemporalAdjusters.next(dayOfWeek)).with(TemporalAdjusters.next(dayOfWeek));
        } else {
            throw new IllegalArgumentException("Invalid dayInMonth");
        }

        return computed.isEqual(currentDate);
    }

    public enum DayInMonth {
        FIRST,
        LAST,
        SECOND,
        THIRD,
        FOURTH,
    }
}
