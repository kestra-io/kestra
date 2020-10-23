package org.kestra.core.models.conditions.types;

import lombok.*;
import lombok.experimental.SuperBuilder;
import org.kestra.core.models.annotations.Documentation;
import org.kestra.core.models.annotations.Example;
import org.kestra.core.models.annotations.InputProperty;
import org.kestra.core.models.conditions.Condition;
import org.kestra.core.models.conditions.ConditionContext;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Documentation(
    description = "Condition for an execution namespace"
)
@Example(
    full = true,
    code = {
        "- conditions:",
        "    - type: org.kestra.core.models.conditions.types.ExecutionNamespaceCondition",
        "      namespace: org.kestra.tests",
        "      prefix: true",

    }
)
public class ExecutionNamespaceCondition extends Condition {
    @NotNull
    @InputProperty(
        description = "The namespace of the flow or the prefix if `prefix` is true"
    )
    public String namespace;

    @Valid
    @Builder.Default
    @InputProperty(
        description = "If we must look at the flow namespace by prefix (simple startWith case sensitive)"
    )
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
