package io.kestra.plugin.core.condition;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.utils.ListUtils;
import io.kestra.core.utils.TruthUtils;
import io.kestra.core.validations.ExecutionFiltersConditionValidation;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.kestra.core.topologies.FlowTopologyService.SIMULATED_EXECUTION;
import static io.kestra.core.utils.Rethrow.throwPredicate;

@SuperBuilder
@ToString
@EqualsAndHashCode(callSuper = true)
@Getter
@NoArgsConstructor
@Schema(
    title = "Run a flow if the execution filter conditions are met.",
    description = """
        This example will trigger an execution of `myflow` once all execution filter conditions are met in a specific period of time (`sla`) — by default, a `window` of 24 hours."""
)
@Plugin(
    examples = {
        @Example(
            full = true,
            title = "A flow that is waiting for two other flows to run successfully within a 1-day-period with fine-grained filter conditions.",
            code = """
                id: myflow
                namespace: company.team

                triggers:
                  - id: wait_for_upstream
                    type: io.kestra.plugin.core.trigger.Flow
                    conditions:
                      - id: poll_for_flows
                        type: io.kestra.plugin.core.condition.ExecutionFilters
                        filters:
                          - id: flow1
                            operand: AND
                            conditions:
                              - field: NAMESPACE
                                type: EQUAL_TO
                                value: company.team
                              - field: FLOW_ID
                                type: EQUAL_TO
                                value: flow1
                              - field: STATE
                                type: IN
                                values: [SUCCESS, WARNING, CANCELLED]
                          - id: flow2
                            operand: AND
                            conditions:
                              - field: NAMESPACE
                                type: EQUAL_TO
                                value: company.team
                              - field: FLOW_ID
                                type: EQUAL_TO
                                value: flow2
                              - field: STATE
                                type: EQUAL_TO
                                values: SUCCESS
                              - field: EXPRESSION
                                type: IS_TRUE
                                value: "{{outputs.output.values.variable == 'value'}}"
                              - field: LABEL
                                type: EQUAL_TO
                                value: "myLabelKey:myLabelValue"

                tasks:
                  - id: hello
                    type: io.kestra.plugin.core.log.Log
                    message: I'm triggered after two other flows!"""
        )
    }
)
public class ExecutionFilters extends AbstractMultipleCondition {
    @NotNull
    @NotEmpty
    @Valid
    @PluginProperty
    @Schema(title = "A list of execution filters.")
    private List<ExecutionFilters.Filter> filters;

    /**
     * Will emulate multiple conditions based on the filters property.
     */
    @JsonIgnore
    @Override
    public Map<String, io.kestra.core.models.conditions.Condition> getConditions() {
        return filters.stream()
            .map(filter -> Map.entry(
                filter.getId(),
                new ExecutionFilters.FilterCondition(filter)
            ))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Hidden
    public static class FilterCondition extends io.kestra.core.models.conditions.Condition {
        private final ExecutionFilters.Filter filter;

        private FilterCondition(ExecutionFilters.Filter filter) {
            this.filter = Objects.requireNonNull(filter);
        }

        @Override
        public boolean test(ConditionContext conditionContext) throws InternalException {
            // we need to only evaluate on namespace and flow for simulated executions
            boolean simulated = ListUtils.emptyOnNull(conditionContext.getExecution().getLabels()).contains(SIMULATED_EXECUTION);
            Stream<Condition> toEvaluate = simulated ? filter.conditions.stream().filter(condition -> condition.field == Field.NAMESPACE || condition.field == Field.FLOW_ID) : filter.conditions.stream();

            return switch (filter.operand) {
                case AND -> toEvaluate.allMatch(throwPredicate(condition -> evaluate(conditionContext, condition)));
                case OR -> toEvaluate.anyMatch(throwPredicate(condition -> evaluate(conditionContext, condition)));
            };
        }

        private boolean evaluate(ConditionContext conditionContext, Condition condition) throws IllegalVariableEvaluationException {
            String fieldValue = switch (condition.field) {
                case FLOW_ID -> conditionContext.getExecution().getFlowId();
                case NAMESPACE -> conditionContext.getExecution().getNamespace();
                case STATE -> conditionContext.getExecution().getState().getCurrent().toString();
                case EXPRESSION -> conditionContext.getRunContext().render(condition.value);
                case LABEL -> conditionContext.getExecution().getLabels().stream()
                    .filter(label -> label.key().equals(extractLabelKey(condition.value)))
                    .findFirst()
                    .map(label -> label.key() + ":" + label.value())
                    .orElse(null);
            };

            return switch (condition.type) {
                case EQUAL_TO -> condition.value.equals(fieldValue);
                case NOT_EQUAL_TO -> !condition.value.equals(fieldValue);
                case IN -> condition.values.contains(fieldValue);
                case NOT_IN -> !condition.values.contains(fieldValue);
                case IS_TRUE -> TruthUtils.isTruthy(fieldValue);
                case IS_FALSE -> !TruthUtils.isTruthy(fieldValue);
                case IS_NULL -> fieldValue == null;
                case IS_NOT_NULL -> fieldValue != null;
                case STARTS_WITH -> fieldValue != null && fieldValue.startsWith(condition.value);
                case ENDS_WITH -> fieldValue != null && fieldValue.endsWith(condition.value);
                case REGEX -> fieldValue != null && fieldValue.matches(condition.value);
                case CONTAINS -> fieldValue != null && fieldValue.contains(condition.value);
            };
        }

        private String extractLabelKey(String value) {
            if (value.indexOf(':') < 0) {
                // will allow checking the existence of a label
                return value;
            }
            // will allow checking the value of a label
            return value.substring(0, value.indexOf(':'));
        }
    }

    @Builder
    @Getter
    @Jacksonized
    public static class Filter {
        @NotNull
        @NotEmpty
        @PluginProperty
        @Schema(title = "A unique identifier for the filter")
        private String id;

        @NotNull
        @Builder.Default
        @PluginProperty
        @Schema(title = "The operand to apply between all conditions of the filter.")
        private Operand operand = Operand.AND;

        @NotNull
        @NotEmpty
        @Valid
        @PluginProperty
        @Schema(title = "The list of conditions.")
        private List<Condition> conditions;
    }

    public enum Operand {
        AND,
        OR
    }

    @Builder
    @Getter
    @ExecutionFiltersConditionValidation
    public static class Condition {
        @NotNull
        @PluginProperty
        @Schema(
            title = "The field which will be filtered.",
            description = """
                Labels are matched using the syntax: `key:value`. You can filter for executions matching specific key-value pairs using the `EQUAL_TO` type:
                ```yaml
                  - field: LABEL
                    type: EQUAL_TO
                    value: "myLabelKey:myLabelValue"
                 ```

                To filter for executions with a given label key regardless of the label value, you can use the `IS_NOT_NULL` type. For example, to filter for executions with a label with the key `productId`, you can use the following filter:
                ```yaml
                  - field: LABEL
                    type: IS_NOT_NULL
                    value: productId
                ```"""
        )
        private Field field;

        @NotNull
        @PluginProperty
        @Schema(
            title = "The type of condition.",
            description = "Can be set to one of the following: `EQUAL_TO`, `NOT_EQUAL_TO`, `IS_NULL`, `IS_NOT_NULL`, `IS_TRUE`, `IS_FALSE`, `STARTS_WITH`, `ENDS_WITH`, `REGEX`, `CONTAINS`. Depending on the `type`, you will need to also set the `value` or `values` property."
        )
        private Type type;

        @PluginProperty
        @Schema(
            title = "The single value to filter the `field` on.",
            description = "Must be set according to its `type`."
        )
        private String value;

        @PluginProperty
        @Schema(
            title = "The list of values to filter the `field` on.",
            description = "Must be set for the following types: IN, NOT_IN."
        )
        private List<String> values;
    }

    public enum Field {
        FLOW_ID,
        NAMESPACE,
        STATE,
        EXPRESSION,
        LABEL,
    }

    public enum Type {
        EQUAL_TO,
        NOT_EQUAL_TO,
        IN,
        NOT_IN,
        IS_TRUE,
        IS_FALSE,
        IS_NULL,
        IS_NOT_NULL,
        STARTS_WITH,
        ENDS_WITH,
        REGEX,
        CONTAINS,
    }
}
