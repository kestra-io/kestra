package io.kestra.core.models.conditions.types;

import io.kestra.core.exceptions.IllegalConditionEvaluation;
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

import java.time.ZonedDateTime;
import javax.validation.constraints.NotNull;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Condition for allows events between two specific datetimes"
)
@Plugin(
    examples = {
        @Example(
            full = true,
            code = {
                "- conditions:",
                "    - type: io.kestra.core.models.conditions.types.DateTimeBetweenCondition",
                "      after: \"2013-09-08T16:19:12\"",
            }
        )
    }
)
public class DateTimeBetweenCondition extends Condition implements ScheduleCondition {
    @NotNull
    @Schema(
        title = "The date to test",
        description = "Can be any variable or any valid ISO 8601 datetime, default will use `{{ now }}`"
    )
    @Builder.Default
    @PluginProperty(dynamic = true)
    private final String date = "{{ now }}";

    @Schema(title = "The date must after this one")
    @PluginProperty(dynamic = false)
    private ZonedDateTime after;

    @Schema(title = "The date must before this one")
    @PluginProperty(dynamic = false)
    private ZonedDateTime before;

    @Override
    public boolean test(ConditionContext conditionContext) throws InternalException {
        String render = conditionContext.getRunContext().render(date, conditionContext.getVariables());
        ZonedDateTime currentDate = DateUtils.parseZonedDateTime(render);

        if (this.before != null && this.after != null) {
            return currentDate.isAfter(after) && currentDate.isBefore(before);
        } else if (this.before != null) {
            return currentDate.isBefore(before);
        } else if (this.after != null) {
            return currentDate.isAfter(after);
        } else {
            throw new IllegalConditionEvaluation("Invalid condition with no before nor after");
        }

    }
}
