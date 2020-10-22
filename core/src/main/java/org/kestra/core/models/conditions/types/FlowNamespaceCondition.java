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
public class FlowNamespaceCondition extends Condition {
    @NotNull
    public String namespace;

    @Valid
    @Builder.Default
    public boolean prefix = false;

    @Override
    public boolean test(ConditionContext conditionContext) {
        if (!prefix && conditionContext.getFlow().getNamespace().equals(this.namespace)) {
            return  true;
        }

        if (prefix && conditionContext.getFlow().getNamespace().startsWith(this.namespace)) {
            return  true;
        }

        return false;
    }
}
