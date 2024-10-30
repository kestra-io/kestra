package io.kestra.plugin.core.condition;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.conditions.Condition;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.triggers.multipleflows.MultipleCondition;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionStorageInterface;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionWindow;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.*;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Slf4j
public abstract class AbstractMultipleCondition extends Condition implements MultipleCondition {
    @NotNull
    @NotBlank
    @Pattern(regexp="^[a-zA-Z0-9][a-zA-Z0-9_-]*")
    @Schema(title = "A unique id for the whole flow")
    @PluginProperty
    private String id;

    @Schema(
        title = "SLA to define the time period for evaluating the conditions",
        description = """
        You can evaluate the conditions on three different ways:
        1. Using a duration window (`type: DURATION_WINDOW`), this is the default, and is configured by default for a 1 day duration window. A duration window express the evaluation period as a start time and a end time that are moving each time the evaluation time reach the end time, keeping the size of the window to the defined duration. For example, a one day duration windows will always evaluate executions during 24h starting at 00:00:00.
        2. Using a sliding window (`type: SLIDING_WINDOW`). A sliding window express a period of time that is fixed in size and depends on the evaluation time. For example, a sliding window of 1 hour will evaluate executions of the past hour (so between now and one hour before now).
        3. Using a daily deadline (`type: DAILY_TIME_DEADLINE`). A daily deadline express the evaluation period as "before a specific time in a day". For example, a daily deadline of 09:00:00 will evaluate executions between 00:00:00 and 09:00:00 each day.
        4. Using a daily window (`type: DAILY_TIME_WINDOW`). A daily window express the evaluation period as "between two specific time in a day". For example, a daily windows of 06:00:00 and 09:00:00 will evaluate executions between 00:06:00 and 09:00:00 each day.
        """
    )
    @PluginProperty
    @Builder.Default
    @Valid
    protected SLA sla = SLA.builder().build();

    /**
     * This conditions will only validate previously calculated value on
     * {@link io.kestra.core.services.FlowTriggerService#computeExecutionsFromFlowTriggers(Execution, List, Optional)}} and {@link MultipleConditionStorageInterface#save(List)} by the executor.
     * The real validation is done here.
     */
    @Override
    public boolean test(ConditionContext conditionContext) throws InternalException {
        MultipleConditionStorageInterface multipleConditionStorage = conditionContext.getMultipleConditionStorage();
        Objects.requireNonNull(multipleConditionStorage);

        Optional<MultipleConditionWindow> triggerExecutionWindow = multipleConditionStorage.get(conditionContext.getFlow(), this.getId());

        Map<String, Boolean> results = getConditions()
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

        boolean result = getConditions().size() == validatedCount;

        if (result && log.isDebugEnabled()) {
            log.debug(
                "[namespace: {}] [flow: {}] Multiple conditions validated !",
                conditionContext.getFlow().getNamespace(),
                conditionContext.getFlow().getId()
            );
        } else if (log.isTraceEnabled()) {
            log.trace(
                "[namespace: {}] [flow: {}] Multiple conditions failed ({}/{}) with '{}'",
                conditionContext.getFlow().getNamespace(),
                conditionContext.getFlow().getId(),
                validatedCount,
                getConditions().size(),
                results
            );
        }

        return result;
    }
}
