package org.kestra.core.models.conditions.types;

import lombok.*;
import lombok.experimental.SuperBuilder;
import org.kestra.core.exceptions.IllegalVariableEvaluationException;
import org.kestra.core.models.conditions.Condition;
import org.kestra.core.models.conditions.ConditionContext;
import org.slf4j.Logger;

import javax.validation.constraints.NotNull;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
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
