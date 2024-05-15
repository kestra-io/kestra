package io.kestra.core.models.conditions.types;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.conditions.Condition;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.conditions.ScheduleCondition;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.List;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import static io.kestra.core.utils.Rethrow.throwPredicate;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Condition to have at least one condition validated."
)
@Plugin(
    examples = {
        @Example(
            full = true,
            code = {
                "- conditions:",
                "    - type: io.kestra.core.models.conditions.types.OrCondition",
                "      conditions:",
                "      -  type: io.kestra.core.models.conditions.types.DayWeekCondition",
                "         dayOfWeek: \"MONDAY\"",
                "      -  type: io.kestra.core.models.conditions.types.DayWeekCondition",
                "         dayOfWeek: \"SUNDAY\"",
            }
        )
    },
    aliases = "io.kestra.core.models.conditions.types.OrCondition"
)
public class OrCondition extends Condition implements ScheduleCondition {
    @NotNull
    @NotEmpty
    @Schema(
        title = "The list of conditions to validate.",
        description = "If any condition is true, it will allow the event's execution."
    )
    @PluginProperty
    private List<Condition> conditions;

    @Override
    public boolean test(ConditionContext conditionContext) throws InternalException {
        return this.conditions
            .stream()
            .anyMatch(throwPredicate(condition -> condition.test(conditionContext)));
    }
}
