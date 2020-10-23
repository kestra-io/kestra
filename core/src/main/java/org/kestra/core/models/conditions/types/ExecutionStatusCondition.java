package org.kestra.core.models.conditions.types;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.kestra.core.models.annotations.Documentation;
import org.kestra.core.models.annotations.Example;
import org.kestra.core.models.annotations.InputProperty;
import org.kestra.core.models.conditions.Condition;
import org.kestra.core.models.conditions.ConditionContext;
import org.kestra.core.models.flows.State;

import java.util.List;
import javax.validation.Valid;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Documentation(description = "Condition based on execution status")
@Example(
    full = true,
    code = {
        "- conditions:",
        "    - type: org.kestra.core.models.conditions.types.ExecutionStatusCondition",
        "      in:",
        "        - SUCCESS",
        "      notIn: ",
        "        - FAILED"
    }
)
public class ExecutionStatusCondition extends Condition {
    @Valid
    @InputProperty(description = "List of state that are authorized")
    public List<State.Type> in;

    @Valid
    @InputProperty(description = "List of state that aren't authorized")
    public List<State.Type> notIn;

    @Override
    public boolean test(ConditionContext conditionContext) {
        if (conditionContext.getExecution() == null) {
            throw new IllegalArgumentException("Invalid condition with execution null");
        }

        boolean result = true;

        if (this.in != null && !this.in.contains(conditionContext.getExecution().getState().getCurrent())) {
            result = false;
        }

        if (this.notIn != null && this.notIn.contains(conditionContext.getExecution().getState().getCurrent())) {
            result = false;
        }

        return result;
    }
}
