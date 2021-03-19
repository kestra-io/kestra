package io.kestra.core.models.conditions.types;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.conditions.Condition;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.flows.State;

import java.util.List;
import javax.validation.Valid;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(title = "Condition based on execution status")
@Plugin(
    examples = {
        @Example(
            full = true,
            code = {
                "- conditions:",
                "    - type: io.kestra.core.models.conditions.types.ExecutionStatusCondition",
                "      in:",
                "        - SUCCESS",
                "      notIn: ",
                "        - FAILED"
            }
        )
    }
)
public class ExecutionStatusCondition extends Condition {
    @Valid
    @Schema(title = "List of state that are authorized")
    public List<State.Type> in;

    @Valid
    @Schema(title = "List of state that aren't authorized")
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
