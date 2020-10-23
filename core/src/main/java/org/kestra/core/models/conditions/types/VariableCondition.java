package org.kestra.core.models.conditions.types;

import lombok.*;
import lombok.experimental.SuperBuilder;
import org.kestra.core.exceptions.IllegalVariableEvaluationException;
import org.kestra.core.models.annotations.Documentation;
import org.kestra.core.models.annotations.Example;
import org.kestra.core.models.conditions.Condition;
import org.kestra.core.models.conditions.ConditionContext;
import org.slf4j.Logger;

import javax.validation.constraints.NotNull;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Documentation(
    description = "Condition based on variables expression",
    body = "If the result is an empty string, a string containing only space or `false`, the condition will be false"
)
@Example(
    title = "A condition that will return false for a missing variable",
    full = true,
    code = {
        "- conditions:",
        "    - type: org.kestra.core.models.conditions.types.VariableCondition",
        "      expression: {{ and unknown }}",
    }
)
public class VariableCondition extends Condition {
    @NotNull
    public String expression;

    @Override
    public boolean test(ConditionContext conditionContext) {
        Logger logger = conditionContext.getRunContext().logger();

        try {
            String render = conditionContext.getRunContext().render(expression);
            System.out.println(render);
            return !(render.isBlank() || render.isEmpty() || render.trim().equals("false"));
        } catch (IllegalVariableEvaluationException e) {
            logger.warn("Illegal variable expression", e);

            return false;
        }
    }
}
