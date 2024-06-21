package io.kestra.plugin.core.condition;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.annotations.PluginProperty;
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

import jakarta.validation.constraints.NotNull;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Condition for a specific flow. Note that this condition is deprecated, use `io.kestra.plugin.core.condition.ExecutionFlowCondition` instead."
)
@Plugin(
    examples = {
        @Example(
            full = true,
            code = {
                "- conditions:",
                "    - type: io.kestra.plugin.core.condition.FlowCondition",
                "      namespace: company.team",
                "      flowId: my-current-flow"
            }
        )
    },
    aliases = "io.kestra.core.models.conditions.types.FlowCondition"
)
@Deprecated
public class FlowCondition extends Condition {
    @NotNull
    @Schema(title = "The namespace of the flow.")
    @PluginProperty
    private String namespace;

    @NotNull
    @Schema(title = "The flow id.")
    @PluginProperty
    private String flowId;

    @Override
    public boolean test(ConditionContext conditionContext) throws InternalException {
        return conditionContext.getFlow().getNamespace().equals(this.namespace) && conditionContext.getFlow().getId().equals(this.flowId);
    }
}
