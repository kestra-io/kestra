package io.kestra.plugin.core.condition;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.conditions.Condition;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.conditions.ScheduleCondition;
import io.kestra.core.models.property.Property;
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
    title = "Condition to allow events on weekend."
)
@Plugin(
    examples = {
        @Example(
            title = "Trigger the flow only on weekend, i.e. on Saturdays and Sundays.",
            full = true,
            code = """
                id: schedule_condition_weekend
                namespace: company.team

                tasks:
                  - id: log_message
                    type: io.kestra.plugin.core.log.Log
                    message: "This flow will execute only on weekends at 11:00 am."

                triggers:
                  - id: schedule
                    type: io.kestra.plugin.core.trigger.Schedule
                    cron: "0 11 * * *"
                    conditions:
                      - type: io.kestra.plugin.core.condition.Weekend
                """
        )
    },
    aliases = {"io.kestra.core.models.conditions.types.WeekendCondition", "io.kestra.plugin.core.condition.WeekendCondition"}
)
public class Weekend extends Condition implements ScheduleCondition {
    @NotNull
    @Schema(
        title = "The date to test.",
        description = "Can be any variable or any valid ISO 8601 datetime. By default, it will use the trigger date."
    )
    @Builder.Default
    private final Property<String> date = Property.ofExpression("{{ trigger.date }}");

    @Override
    public boolean test(ConditionContext conditionContext) throws InternalException {
        String render = conditionContext.getRunContext().render(date).as(String.class, conditionContext.getVariables()).orElseThrow();
        LocalDate currentDate = DateUtils.parseLocalDate(render);

        return currentDate.getDayOfWeek().equals(DayOfWeek.SATURDAY) ||
            currentDate.getDayOfWeek().equals(DayOfWeek.SUNDAY);
    }
}
