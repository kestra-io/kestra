package io.kestra.core.models.conditions.types;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.conditions.Condition;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.conditions.ScheduleCondition;
import io.kestra.core.models.executions.Execution;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.Map;

import static io.kestra.core.utils.MapUtils.mergeWithNullableValues;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Condition based on the outputs of an execution.",
    description = "The condition returns `false` if the execution has no output. If the result is an empty string, a space, or `false`, the condition will also be considered as `false`."
)
@Plugin(
    examples = {
        @Example(
            title = "A condition that will return true for an output matching a specific value.",
            full = true,
            code = {
                "- conditions:",
                "    - type: io.kestra.core.models.conditions.types.ExecutionOutputsCondition",
                "      expression: {{ trigger.outputs.status_code == '200' }}",
            }
        )
    },
    aliases = "io.kestra.core.models.conditions.types.ExecutionOutputsCondition"
)
public class ExecutionOutputsCondition extends Condition implements ScheduleCondition {

    private static final String TRIGGER_VAR = "trigger";
    private static final String OUTPUTS_VAR = "outputs";

    @NotNull
    @NotEmpty
    @PluginProperty
    private String expression;

    /** {@inheritDoc} **/
    @Override
    public boolean test(ConditionContext conditionContext) throws InternalException {

        if (hasNoOutputs(conditionContext.getExecution())) {
            return false; // shortcut for not evaluating the expression.
        }

        Map<String, Object> variables = mergeWithNullableValues(
            conditionContext.getVariables(),
            Map.of(TRIGGER_VAR, Map.of(OUTPUTS_VAR, conditionContext.getExecution().getOutputs()))
        );

        String render = conditionContext.getRunContext().render(expression, variables);
        return !(render.isBlank() || render.isEmpty() || render.trim().equals("false"));
    }

    private boolean hasNoOutputs(final Execution execution) {
        return execution.getOutputs() == null || execution.getOutputs().isEmpty();
    }
}
