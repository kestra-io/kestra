package org.kestra.core.models.conditions.types;

import lombok.*;
import lombok.experimental.SuperBuilder;
import org.kestra.core.models.conditions.Condition;
import org.kestra.core.models.conditions.ConditionContext;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public class ExecutionNamespaceCondition extends Condition {
    @NotNull
    public String namespace;

    @Valid
    @Builder.Default
    public boolean prefix = false;

    @Override
    public boolean test(ConditionContext conditionContext) {
        if (conditionContext.getExecution() == null) {
            throw new IllegalArgumentException("Invalid condition with execution null");
        }

        if (!prefix && conditionContext.getExecution().getNamespace().equals(this.namespace)) {
            return  true;
        }

        if (prefix && conditionContext.getExecution().getNamespace().startsWith(this.namespace)) {
            return  true;
        }

        return false;
    }
}
