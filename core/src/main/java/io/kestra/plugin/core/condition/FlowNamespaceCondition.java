package io.kestra.plugin.core.condition;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.annotations.PluginProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
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
    title = "Match a flow namespace (deprecated).",
    description = """
        Checks the current flow’s namespace, with optional prefix matching.

        Deprecated; prefer `io.kestra.plugin.core.condition.ExecutionNamespace`, which evaluates against executions in Flow triggers."""
)
@Plugin(
    examples = {
        @Example(
            full = true,
            code = {
                "- conditions:",
                "    - type: io.kestra.plugin.core.condition.FlowNamespaceCondition",
                "      namespace: io.kestra.tests",
                "      prefix: true",

            }
        )
    },
    aliases = "io.kestra.core.models.conditions.types.FlowNamespaceCondition"
)
@Deprecated
public class FlowNamespaceCondition extends Condition {
    @NotNull
    @Schema(
        title = "The namespace of the flow or the prefix if `prefix` is true."
    )
    @PluginProperty
    private String namespace;

    @Builder.Default
    @Schema(
        title = "If we must look at the flow namespace by prefix (checked using startsWith). The prefix is case sensitive."
    )
    @PluginProperty
    private final Boolean prefix = false;

    @Override
    public boolean test(ConditionContext conditionContext) throws InternalException {
        if (!prefix && conditionContext.getFlow().getNamespace().equals(this.namespace)) {
            return  true;
        }

        if (prefix && conditionContext.getFlow().getNamespace().startsWith(this.namespace)) {
            return  true;
        }

        return false;
    }
}
