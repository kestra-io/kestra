package io.kestra.plugin.core.condition;

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

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Condition based on variable expression.",
    description = "If the result is an empty string, a string containing only space or `false`, the condition will be considered as false."
)
@Plugin(
    examples = {
        @Example(
            title = "A condition that will return false for a missing variable.",
            full = true,
            code = {
                "- conditions:",
                "    - type: io.kestra.plugin.core.condition.ExpressionCondition",
                "      expression: {{ unknown is defined }}",
            }
        )
    },
    aliases = "io.kestra.core.models.conditions.types.VariableCondition"
)
public class ExpressionCondition extends Condition implements ScheduleCondition {
    @NotNull
    @NotEmpty
    @PluginProperty
    private String expression;

    @Override
    public boolean test(ConditionContext conditionContext) throws InternalException {
        String render = conditionContext.getRunContext().render(expression, conditionContext.getVariables());
        return !(render.isBlank() || render.isEmpty() || render.trim().equals("false"));
    }
}
