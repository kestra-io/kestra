package io.kestra.plugin.core.condition;

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
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.DayOfWeek;
import java.time.LocalDate;
import jakarta.validation.constraints.NotNull;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Condition to allow events on a particular day of the week."
)
@Plugin(
    examples = {
        @Example(
            title = "Trigger condition to execute the flow only on a specific day of the week.",
            full = true,
            code = """
                id: schedule_condition_dayweek
                namespace: company.team

                tasks:
                  - id: log_message
                    type: io.kestra.plugin.core.log.Log
                    message: "This flow will execute only on Mondays at 11:00 am."

                triggers:
                  - id: schedule
                    type: io.kestra.plugin.core.trigger.Schedule
                    cron: "0 11 * * *"
                    conditions:
                      - type: io.kestra.plugin.core.condition.DayWeek
                        dayOfWeek: "MONDAY"
                """
        )
    },
    aliases = {"io.kestra.core.models.conditions.types.DayWeekCondition", "io.kestra.plugin.core.condition.DayWeekCondition"}
)
public class DayWeek extends Condition implements ScheduleCondition {
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

    @Override
    public boolean test(ConditionContext conditionContext) throws InternalException {
        RunContext runContext = conditionContext.getRunContext();
        String render = runContext.render(date).as(String.class, conditionContext.getVariables()).orElseThrow();
        LocalDate currentDate = DateUtils.parseLocalDate(render);

        return currentDate.getDayOfWeek().equals(runContext.render(dayOfWeek).as(DayOfWeek.class, conditionContext.getVariables()).orElseThrow());
    }
}
