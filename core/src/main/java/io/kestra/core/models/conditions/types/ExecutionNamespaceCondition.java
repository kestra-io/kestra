package io.kestra.core.models.conditions.types;

import io.kestra.core.exceptions.IllegalConditionEvaluation;
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
import java.util.Optional;
import java.util.function.BiPredicate;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Condition for an execution namespace."
)
@Plugin(
    examples = {
        @Example(
            full = true,
            code = {
                "- conditions:",
                "    - type: io.kestra.core.models.conditions.types.ExecutionNamespaceCondition",
                "      namespace: io.kestra.tests",
                "      comparison: PREFIX"
            }
        )
    }
)
public class ExecutionNamespaceCondition extends Condition {
    @NotNull
    @Schema(
        title = "String against which to match the execution namespace depending on the provided comparison."
    )
    @PluginProperty
    private String namespace;

    @Schema(
        title = "Comparison to use when checking if namespace matches. If not provided, it will use `EQUALS` by default."
    )
    @PluginProperty
    private Comparison comparison;

    @Schema(
        title = "Whether to look at the flow namespace by prefix. Shortcut for `comparison: PREFIX`.",
        description = "Only used when `comparison` is not set"
    )
    @PluginProperty
    @Builder.Default
    private boolean prefix = false;

    @Override
    public boolean test(ConditionContext conditionContext) throws InternalException {
        if (conditionContext.getExecution() == null) {
            throw new IllegalConditionEvaluation("Invalid condition with null execution");
        }

        return Optional.ofNullable(this.comparison).orElse(prefix ? Comparison.PREFIX : Comparison.EQUALS)
            .test(conditionContext.getExecution().getNamespace(), this.namespace);
    }

    public enum Comparison {
        EQUALS(String::equals),
        PREFIX(String::startsWith),
        SUFFIX(String::endsWith);
        private final BiPredicate<String, String> checker;


        Comparison(BiPredicate<String, String> checker) {
            this.checker = checker;
        }

        public boolean test(String actualNamespace, String matcher) {
            return this.checker.test(actualNamespace, matcher);
        }
    }
}
