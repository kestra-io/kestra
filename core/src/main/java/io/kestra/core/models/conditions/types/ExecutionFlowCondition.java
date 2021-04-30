package io.kestra.core.models.conditions.types;

import io.kestra.core.exceptions.IllegalConditionEvaluation;
import io.kestra.core.exceptions.InternalException;
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

import javax.validation.constraints.NotNull;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Condition for a specific flow of an execution"
)
@Plugin(
    examples = {
        @Example(
            full = true,
            code = {
                "- conditions:",
                "    - type: io.kestra.core.models.conditions.types.ExecutionFlowCondition",
                "      namespace: io.kestra.tests",
                "      flowId: my-current-flow"
            }
        )
    }
)
public class ExecutionFlowCondition extends Condition {
    @NotNull
    @Schema(title = "The namespace of the flow")
    private String namespace;

    @NotNull
    @Schema(title = "The flow id")
    private String flowId;

    @Override
    public boolean test(ConditionContext conditionContext) throws InternalException {
        if (conditionContext.getExecution() == null) {
            throw new IllegalConditionEvaluation("Invalid condition with execution null");
        }

        return conditionContext.getExecution().getNamespace().equals(this.namespace) && conditionContext.getExecution().getFlowId().equals(this.flowId);
    }
}
