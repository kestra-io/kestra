package io.kestra.plugin.core.condition;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.conditions.Condition;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.conditions.ScheduleCondition;
import io.kestra.core.models.property.Property;
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
    description = """
        Renders a templated expression and treats the result as truthy/falsey to decide whether the condition passes.

        Blank strings or the literal `false` (case-sensitive) evaluate to false; everything else is true. Expressions can reference any flow variables available at evaluation time, so make sure they resolve without errors."""
)
@Plugin(
    examples = {
        @Example(
            title = "Trigger condition to execute the flow when the expression evaluates to true.",
            full = true,
            code = """
                id: myflow
                namespace: company.team

                tasks:
                  - id: hello
                    type: io.kestra.plugin.core.log.Log
                    message: Average value has gone below 10

                triggers:
                  - id: expression_trigger
                    type: io.kestra.plugin.core.trigger.Schedule
                    cron: "*/1 * * * *"
                    conditions:
                      - type: io.kestra.plugin.core.condition.Expression
                        expression: "{{ kv('average_value') < 10 }}"
                """
        )
    },
    aliases = {"io.kestra.core.models.conditions.types.VariableCondition", "io.kestra.plugin.core.condition.ExpressionCondition"}
)
public class Expression extends Condition {
    @NotNull
    private Property<String> expression;

    @Override
    public boolean test(ConditionContext conditionContext) throws InternalException {
        String render = conditionContext.getRunContext().render(expression).as(String.class, conditionContext.getVariables()).orElseThrow();
        return !(render.isBlank() || render.trim().equals("false"));
    }
}
