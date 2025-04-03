package io.kestra.plugin.core.condition;

import io.kestra.core.exceptions.IllegalConditionEvaluation;
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

import java.time.ZonedDateTime;
import java.util.Map;

import jakarta.validation.constraints.NotNull;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Condition to allow events between two specific datetime values."
)
@Plugin(
    examples = {
        @Example(
            title = "Trigger condition to execute the flow only after the specific date.",
            full = true,
            code = """
                id: schedule_condition_datetimebetween
                namespace: company.team

                tasks:
                  - id: log_message
                    type: io.kestra.plugin.core.log.Log
                    message: "This flow will execute once every 5 minutes after the date 2025-12-31T23:59:59Z"

                triggers:
                  - id: schedule
                    type: io.kestra.plugin.core.trigger.Schedule
                    cron: "*/5 * * * *"
                    conditions:
                      - type: io.kestra.plugin.core.condition.DateTimeBetween
                        date: "{{ trigger.date }}"
                        after: "2025-12-31T23:59:59Z"
                """
        ),
        @Example(
            title = "Trigger condition to execute the flow between two specific dates.",
            full = true,
            code = """
                id: schedule_condition_datetimebetween
                namespace: company.team

                tasks:
                  - id: log_message
                    type: io.kestra.plugin.core.log.Log
                    message: "This flow will be executed once every 5 minutes between the before and after dates"

                triggers:
                  - id: schedule
                    type: io.kestra.plugin.core.trigger.Schedule
                    cron: "*/5 * * * *"
                    conditions:
                      - type: io.kestra.plugin.core.condition.DateTimeBetween
                        date: "{{ trigger.date }}"
                        after: "2025-01-01T00:00:00Z"
                        before: "2025-12-31T23:59:59Z"
                """
        ),
    },
    aliases = {"io.kestra.core.models.conditions.types.DateTimeBetweenCondition", "io.kestra.plugin.core.condition.DateTimeBetweenCondition"}
)
public class DateTimeBetween extends Condition implements ScheduleCondition {
    @NotNull
    @Schema(
        title = "The date to test.",
        description = "Can be any variable or any valid ISO 8601 datetime. By default, it will use the trigger date."
    )
    @Builder.Default
    @PluginProperty(dynamic = true)
    private final String date = "{{ trigger.date }}";

    @Schema(
        title = "The date to test must be after this one.",
        description = "Must be a valid ISO 8601 datetime with the zone identifier (use 'Z' for the default zone identifier)."
    )
    private Property<ZonedDateTime> after;

    @Schema(
        title = "The date to test must be before this one.",
        description = "Must be a valid ISO 8601 datetime with the zone identifier (use 'Z' for the default zone identifier)."
    )
    private Property<ZonedDateTime> before;

    @Override
    public boolean test(ConditionContext conditionContext) throws InternalException {
        Map<String, Object> vars = conditionContext.getVariables();
        String render = conditionContext.getRunContext().render(date, vars);
        ZonedDateTime currentDate = DateUtils.parseZonedDateTime(render);

        ZonedDateTime afterRendered = conditionContext.getRunContext().render(this.after).as(ZonedDateTime.class, vars).orElse(null);
        ZonedDateTime beforeRendered = conditionContext.getRunContext().render(this.before).as(ZonedDateTime.class, vars).orElse(null);

        if (beforeRendered != null && afterRendered != null) {
            return currentDate.isAfter(afterRendered) && currentDate.isBefore(beforeRendered);
        } else if (beforeRendered != null) {
            return currentDate.isBefore(beforeRendered);
        } else if (afterRendered != null) {
            return currentDate.isAfter(afterRendered);
        } else {
            throw new IllegalConditionEvaluation("Invalid condition with no before nor after");
        }
    }
}
