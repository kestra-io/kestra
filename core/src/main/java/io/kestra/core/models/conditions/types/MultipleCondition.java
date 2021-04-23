package io.kestra.core.models.conditions.types;

import io.kestra.core.exceptions.InternalException;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.conditions.Condition;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionStorageInterface;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionWindow;
import io.kestra.core.services.FlowService;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.AbstractMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Condition for a list of flow",
    description = "Trigger the first time all the flow are successfully executed during the `window` duration "
)
@Plugin(
    examples = {
        @Example(
            full = true,
            title = "A flow that is waiting for 2 flows that is successful in 1 days",
            code = {
                "triggers:",
                "  - id: multiple-listen-flow",
                "    type: io.kestra.core.models.triggers.types.Flow",
                "    conditions:",
                "      - id: multiple",
                "        type: io.kestra.core.models.conditions.types.MultipleCondition",
                "        window: P1D",
                "        windowAdvance: P0D",
                "        conditions:",
                "          success:",
                "            type: io.kestra.core.models.conditions.types.ExecutionStatusCondition",
                "            in:",
                "              - SUCCESS",
                "          flow-a:",
                "            type: io.kestra.core.models.conditions.types.ExecutionFlowCondition",
                "            namespace: io.kestra.tests",
                "            flowId: trigger-multiplecondition-flow-a",
                "          flow-b:",
                "            type: io.kestra.core.models.conditions.types.ExecutionFlowCondition",
                "            namespace: io.kestra.tests",
                "            flowId: trigger-multiplecondition-flow-b"
            }
        )
    }
)
public class MultipleCondition extends Condition {
    @NotNull
    @NotBlank
    @Pattern(regexp="[a-zA-Z0-9_-]+")
    @Schema(title = "A unique id for the whole flow")
    protected String id;

    @NotNull
    @Schema(title = "The duration of the window")
    private Duration window;

    @NotNull
    @Schema(
        title = "The window advance duration",
        description = "Allow to specify the start hour for example of the window" +
        "example: windowStart=2020-09-08T16:00:00+02 and windowAdvance=PT16H will look at execution between execution at 16:00 every day")
    private Duration windowAdvance;

    @NotNull
    @NotEmpty
    @Schema(
        title = "The list of conditions to wait for",
        description = "The key must be unique for a trigger since it will be use to store previous result."
    )
    @PluginProperty(
        dynamic = false,
        additionalProperties = Condition.class
    )
    private Map<String, Condition> conditions;

    /**
     * This conditions will only validate previously calculated value on
     * {@link FlowService#multipleFlowTrigger(Stream, Flow, Execution, MultipleConditionStorageInterface)}} and save on {@link MultipleConditionStorageInterface}
     * by the executor.
     * The real validation is done here.
     */
    @Override
    public boolean test(ConditionContext conditionContext) throws InternalException {
        Logger logger = conditionContext.getRunContext().logger();

        MultipleConditionStorageInterface multipleConditionStorage = conditionContext.getMultipleConditionStorage();
        Objects.requireNonNull(multipleConditionStorage);

        Optional<MultipleConditionWindow> triggerExecutionWindow = multipleConditionStorage.get(conditionContext.getFlow(), this.getId());

        Map<String, Boolean> results = conditions
            .keySet()
            .stream()
            .map(condition -> new AbstractMap.SimpleEntry<>(
                condition,
                (triggerExecutionWindow.isPresent() &&
                    triggerExecutionWindow.get().getResults() != null &&
                    triggerExecutionWindow.get().getResults().containsKey(condition) &&
                    triggerExecutionWindow.get().getResults().get(condition)
                )
            ))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        long validatedCount = results
            .entrySet()
            .stream()
            .filter(Map.Entry::getValue)
            .count();

        boolean result = conditions.size() == validatedCount;

        if (result && logger.isDebugEnabled()) {
            logger.debug(
                "[namespace: {}] [flow: {}] Multiple conditions validated !",
                conditionContext.getFlow().getNamespace(),
                conditionContext.getFlow().getId()
            );
        } else if (logger.isTraceEnabled()) {
            logger.trace(
                "[namespace: {}] [flow: {}] Multiple conditions failed ({}/{}) with '{}'",
                conditionContext.getFlow().getNamespace(),
                conditionContext.getFlow().getId(),
                validatedCount,
                conditions.size(),
                results
            );
        }

         return result;
    }
}
