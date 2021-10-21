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
    @Schema(
        title = "The duration of the window",
        description = "See [ISO_8601 Durations](https://en.wikipedia.org/wiki/ISO_8601#Durations) for more information of available duration value.\n" +
            "The start of the window is always based on midnight except if you set windowAdvance parameter. Eg if you have a 10 minutes (PT10M) window, " +
            "the first window will be 00:00 to 00:10 and a new window will be started each 10 minutes")
    private Duration window;

    @NotNull
    @Schema(
        title = "The window advance duration",
        description = "Allow to specify the start hour of the window\n" +
        "Eg: you want a window of 6 hours (window=PT6H). By default the check will be done between: \n" +
            "00:00 and 06:00 - 06:00 and 12:00 - 12:00 and 18:00 - 18:00 and 00:00 " +
            "If you want to check the window between: \n" +
            "03:00 and 09:00 - 09:00 and 15:00 - 15:00 and 21:00 - 21:00 and 3:00" +
            "You will have to shift the window of 3 hours by settings windowAdvance: PT3H")
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
