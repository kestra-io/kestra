package org.kestra.core.models.conditions.types;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.kestra.core.models.annotations.Example;
import org.kestra.core.models.annotations.Plugin;
import org.kestra.core.models.conditions.Condition;
import org.kestra.core.models.conditions.ConditionContext;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.models.triggers.multipleflows.MultipleConditionStorageInterface;
import org.kestra.core.models.triggers.multipleflows.TriggerExecutionWindow;
import org.kestra.core.services.FlowService;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.AbstractMap;
import java.util.Map;
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
            code = {
                "- conditions:",
                "    - type: org.kestra.core.models.conditions.types.MultipleFlowCondition",
                "      window: PT1D",
                "      windowAdvance: PT16H",
                "      flows:",
                "      - namespace: org.kestra.tests",
                "        flowId: my-current-flow"
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
        description = "Allow to specify the start hour for example of the window\n" +
        "example: windowStart=2020-09-08T16:00:00+02 and windowAdvance=PT16H will look at execution between execution 16:00 every day")
    private Duration windowAdvance;

    @NotNull
    @NotEmpty
    @Schema(title = "The list of conditions to wait for")
    private Map<String, Condition> conditions;

    /**
     * This conditions will only validate previously calculated value on
     * {@link FlowService#multipleFlowTrigger(Stream, Flow, Execution)} and save on {@link MultipleConditionStorageInterface}
     * by the executor.
     * The real validation is done here.
     */
    @Override
    public boolean test(ConditionContext conditionContext) {
        Logger logger = conditionContext.getRunContext().logger();

        MultipleConditionStorageInterface multipleConditionStorage = conditionContext.getRunContext()
            .getApplicationContext()
            .getBean(MultipleConditionStorageInterface.class);

        Optional<TriggerExecutionWindow> triggerExecutionWindow = multipleConditionStorage.get(conditionContext.getFlow(), this.getId());

        Map<String, Boolean> results = conditions
            .keySet()
            .stream()
            .map(condition -> new AbstractMap.SimpleEntry<>(
                condition,
                (triggerExecutionWindow.isPresent() && triggerExecutionWindow.get()
                    .getResults()
                    .containsKey(condition) && triggerExecutionWindow.get().getResults().get(condition))
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
