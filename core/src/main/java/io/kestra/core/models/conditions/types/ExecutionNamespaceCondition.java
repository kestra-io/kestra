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

import javax.validation.constraints.NotNull;
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
                "      prefix: true",

            }
        )
    }
)
public class ExecutionNamespaceCondition extends Condition {
    @NotNull
    @Schema(
        description = "String against which to match the execution namespace depending on the provided comparison."
    )
    @PluginProperty
    private String namespace;

    @Deprecated
    @Schema(
        description = "If we must look at the flow namespace by prefix (checked using startsWith). The prefix is case sensitive."
    )
    @PluginProperty
    private Boolean prefix;

    @Schema(
        description = "Comparison to use when checking if namespace matches. If not provided, it will use `EQUALS`, or `PREFIX` if `prefix` is true for compatibility reasons."
    )
    @PluginProperty
    private Comparison comparison;

    @Override
    public boolean test(ConditionContext conditionContext) throws InternalException {
        if (conditionContext.getExecution() == null) {
            throw new IllegalConditionEvaluation("Invalid condition with null execution");
        }

        Comparison comparisonToUse = this.comparison == null
            ? (Boolean.TRUE.equals(this.prefix) ? Comparison.PREFIX : Comparison.EQUALS)
            : this.comparison;

        return comparisonToUse.test(conditionContext.getExecution().getNamespace(), this.namespace);
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
