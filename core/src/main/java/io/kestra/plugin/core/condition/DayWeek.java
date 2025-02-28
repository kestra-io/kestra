package io.kestra.plugin.core.condition;

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
            title = "Trigger the flow only on a specific day of the week.",
            full = true,
            code = """
                id: schedule-condition-dayweek
                namespace: company.team

                tasks:
                  - id: log_message
                    type: io.kestra.plugin.core.log.Log
                    message: "This flow will be executed only on Mondays at 11:00 am."

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
    @PluginProperty(dynamic = true)
    private final String date = "{{ trigger.date }}";

    @NotNull
    @Schema(title = "The day of week.")
    @PluginProperty
    private DayOfWeek dayOfWeek;

    @Override
    public boolean test(ConditionContext conditionContext) throws InternalException {
        String render = conditionContext.getRunContext().render(date, conditionContext.getVariables());
        LocalDate currentDate = DateUtils.parseLocalDate(render);

        return currentDate.getDayOfWeek().equals(this.dayOfWeek);
    }
}
