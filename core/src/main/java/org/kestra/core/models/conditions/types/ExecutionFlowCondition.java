package org.kestra.core.models.conditions.types;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.kestra.core.models.conditions.Condition;
import org.kestra.core.models.conditions.ConditionContext;

import javax.validation.constraints.NotNull;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public class ExecutionFlowCondition extends Condition {
    @NotNull
    public String namespace;

    @NotNull
    public String flowId;

    @Override
    public boolean test(ConditionContext conditionContext) {
        if (conditionContext.getExecution() == null) {
            throw new IllegalArgumentException("Invalid condition with execution null");
        }

        return conditionContext.getExecution().getNamespace().equals(this.namespace) && conditionContext.getExecution().getFlowId().equals(this.flowId);
    }
}
