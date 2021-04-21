package io.kestra.core.models.conditions.types;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.conditions.Condition;
import io.kestra.core.models.conditions.ConditionContext;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.List;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Condition to have at least once conditions validated"
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
    }
)
public class OrCondition extends Condition {
    @NotNull
    @NotEmpty
    @Schema(
        title = "The list of conditions to exclude",
        description = "If any conditions is true, it will allow events."
    )
    @PluginProperty(dynamic = false)
    private List<Condition> conditions;

    @Override
    public boolean test(ConditionContext conditionContext) {
        return this.conditions
            .stream()
            .anyMatch(condition -> condition.test(conditionContext));
    }
}
