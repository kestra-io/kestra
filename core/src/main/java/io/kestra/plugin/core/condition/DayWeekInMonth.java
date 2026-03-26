package io.kestra.plugin.core.condition;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Map;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.conditions.Condition;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.conditions.ScheduleCondition;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.utils.DateUtils;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Allow events on an nth weekday within the month.",
    description = """
        Renders a date (defaults to the trigger timestamp) and checks whether it matches the requested weekday and position in the month (`FIRST`, `SECOND`, `THIRD`, `FOURTH`, or `LAST`).

        Useful for patterns like “first Monday” or “last Friday”. Dates must be valid ISO-8601 strings."""
)
@Plugin(
    examples = {
        @Example(
            title = "Trigger condition to execute the flow only on the first Monday of the month.",
            full = true,
            code = """
                id: schedule_condition_dayweekinmonth
                namespace: company.team

                tasks:
                  - id: log_message
                    type: io.kestra.plugin.core.log.Log
                    message: "This flow will execute only on the first Monday of the month at 11:00 am."

                triggers:
                  - id: schedule
                    type: io.kestra.plugin.core.trigger.Schedule
                    cron: "0 11 * * *"
                    conditions:
                      - type: io.kestra.plugin.core.condition.DayWeekInMonth
                        dayOfWeek: "MONDAY"
                        dayInMonth: FIRST
                """
        )
    },
    aliases = { "io.kestra.core.models.conditions.types.DayWeekInMonthCondition", "io.kestra.plugin.core.condition.DayWeekInMonthCondition" }
)
public class DayWeekInMonth extends Condition implements ScheduleCondition {
    @NotNull
    @Schema(
        title = "The date to test.",
        description = "Can be any variable or any valid ISO 8601 datetime. By default, it will use the trigger date."
    )
    @Builder.Default
    private final Property<String> date = Property.ofExpression("{{ trigger.date }}");

    @NotNull
    @Schema(title = "The day of week.")
    private Property<DayOfWeek> dayOfWeek;

    @NotNull
    @Schema(title = "Are you looking for the first or the last day in the month?")
    private Property<DayWeekInMonth.DayInMonth> dayInMonth;

    @Override
    public boolean test(ConditionContext conditionContext) throws InternalException {
        Map<String, Object> vars = conditionContext.getVariables();
        RunContext runContext = conditionContext.getRunContext();

        String render = runContext.render(this.date).as(String.class, vars).orElseThrow();
        LocalDate currentDate = DateUtils.parseLocalDate(render);
        LocalDate computed;

        DayOfWeek renderedDayOfWeek = runContext.render(this.dayOfWeek).as(DayOfWeek.class, vars).orElseThrow();
        DayWeekInMonth.DayInMonth renderedDayInMonth = runContext.render(this.dayInMonth).as(DayWeekInMonth.DayInMonth.class, vars).orElseThrow();

        if (renderedDayInMonth == DayInMonth.FIRST) {
            computed = currentDate.with(TemporalAdjusters.firstInMonth(renderedDayOfWeek));
        } else if (renderedDayInMonth == DayInMonth.SECOND) {
            computed = currentDate.with(TemporalAdjusters.firstInMonth(renderedDayOfWeek))
                .plusWeeks(1);
        } else if (renderedDayInMonth == DayInMonth.THIRD) {
            computed = currentDate.with(TemporalAdjusters.firstInMonth(renderedDayOfWeek))
                .plusWeeks(2);
        } else if (renderedDayInMonth == DayInMonth.FOURTH) {
            computed = currentDate.with(TemporalAdjusters.firstInMonth(renderedDayOfWeek))
                .plusWeeks(3);
        } else if (renderedDayInMonth == DayInMonth.LAST) {
            computed = currentDate.with(TemporalAdjusters.lastInMonth(renderedDayOfWeek));
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
