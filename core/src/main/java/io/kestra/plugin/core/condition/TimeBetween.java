package io.kestra.plugin.core.condition;

import io.kestra.core.exceptions.IllegalConditionEvaluation;
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

import java.time.OffsetTime;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Condition to allow events between two specific times."
)
@Plugin(
    examples = {
        @Example(
            title = "Trigger condition to execute the flow between two specific times.",
            full = true,
            code = """
                id: schedule_condition_timebetween
                namespace: company.team

                tasks:
                  - id: log_message
                    type: io.kestra.plugin.core.log.Log
                    message: "This flow will execute every 5 minutes between 4pm and 8pm."

                triggers:
                  - id: schedule
                    type: io.kestra.plugin.core.trigger.Schedule
                    cron: "*/5 * * * *"
                    conditions:
                      - type: io.kestra.plugin.core.condition.TimeBetween
                        after: "16:00:00+02:00"
                        before: "20:00:00+02:00"
                """
        )
    },
    aliases = {"io.kestra.core.models.conditions.types.TimeBetweenCondition", "io.kestra.plugin.core.condition.TimeBetweenCondition"}
)
public class TimeBetween extends Condition implements ScheduleCondition {
    @NotNull
    @Schema(
        title = "The time to test.",
        description = "Can be any variable or any valid ISO 8601 time. By default, it will use the trigger date."
    )
    @Builder.Default
    private final Property<String> date = Property.ofExpression("{{ trigger.date }}");

    @Schema(
        title = "The time to test must be after this one.",
        description = "Must be a valid ISO 8601 time with offset."
    )
    private Property<OffsetTime> after;

    @Schema(
        title = "The time to test must be before this one.",
        description = "Must be a valid ISO 8601 time with offset."
    )
    private Property<OffsetTime> before;

    @Override
    public boolean test(ConditionContext conditionContext) throws InternalException {
        RunContext runContext = conditionContext.getRunContext();
        Map<String, Object> variables = conditionContext.getVariables();

        // cache must be skipped for date rendering as the value can change for each test
        String dateRendered = runContext.render(date).skipCache().as(String.class, variables).orElseThrow();
        OffsetTime currentDate = DateUtils.parseZonedDateTime(dateRendered).toOffsetDateTime().toOffsetTime();

        OffsetTime beforeRendered = runContext.render(before).as(OffsetTime.class, variables).orElse(null);
        OffsetTime afterRendered = runContext.render(after).as(OffsetTime.class, variables).orElse(null);
        
        if (beforeRendered != null && afterRendered != null) {
            // Case 1: Normal range (e.g., 16:00 -> 20:00)
            if (afterRendered.isBefore(beforeRendered)) {
                return currentDate.isAfter(afterRendered) && currentDate.isBefore(beforeRendered);
            // Case 2: Cross-midnight range (e.g., 22:00 -> 02:00)
            } else {
                return currentDate.isAfter(afterRendered) || currentDate.isBefore(beforeRendered);
            }
            
        } else if (beforeRendered != null) {
            return currentDate.isBefore(beforeRendered);
            
        } else if (afterRendered != null) {
            return currentDate.isAfter(afterRendered);
            
        } else {
            throw new IllegalConditionEvaluation("Invalid condition: no 'before' or 'after' value defined");
        }
    }
}
