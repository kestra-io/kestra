package org.kestra.core.models.conditions.types;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.kestra.core.models.annotations.Example;
import org.kestra.core.models.annotations.Plugin;
import org.kestra.core.models.conditions.Condition;
import org.kestra.core.models.conditions.ConditionContext;

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
                "    - type: org.kestra.core.models.conditions.types.ExecutionFlowCondition",
                "      namespace: org.kestra.tests",
                "      flowId: my-current-flow"
            }
        )
    }
)
public class ExecutionFlowCondition extends Condition {
    @NotNull
    @Schema(title = "The namespace of the flow")
    public String namespace;

    @NotNull
    @Schema(title = "The flow id")
    public String flowId;

    @Override
    public boolean test(ConditionContext conditionContext) {
        if (conditionContext.getExecution() == null) {
            throw new IllegalArgumentException("Invalid condition with execution null");
        }

        return conditionContext.getExecution().getNamespace().equals(this.namespace) && conditionContext.getExecution().getFlowId().equals(this.flowId);
    }
}
