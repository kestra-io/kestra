package io.kestra.core.models.conditions.types;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.conditions.Condition;
import io.kestra.core.models.conditions.ConditionContext;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Condition for a flow namespace"
)
@Plugin(
    examples = {
        @Example(
            full = true,
            code = {
                "- conditions:",
                "    - type: io.kestra.core.models.conditions.types.FlowNamespaceCondition",
                "      namespace: io.kestra.tests",
                "      prefix: true",

            }
        )
    }
)
public class FlowNamespaceCondition extends Condition {
    @NotNull
    @Schema(
        title = "The namespace of the flow or the prefix if `prefix` is true"
    )
    public String namespace;

    @Valid
    @Builder.Default
    @Schema(
        title = "If we must look at the flow namespace by prefix (simple startWith case sensitive)"
    )
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
