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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.*;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
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
    protected String id;

    @NotNull
    @Schema(
        title = "The duration of the window",
        description = """
            See [ISO_8601 Durations](https://en.wikipedia.org/wiki/ISO_8601#Durations) for more information of available duration value.
            The start of the window is always based on midnight except if you set windowAdvance parameter. Eg if you have a 10 minutes (PT10M) window,
            the first window will be 00:00 to 00:10 and a new window will be started each 10 minutes.""")
    @PluginProperty
    @Builder.Default
    private Duration window = Duration.ofDays(1);

    @Schema(
        title = "The window advance duration",
        description = """
            Allow to specify the start hour of the window.
            Eg: you want a window of 6 hours (window=PT6H). By default the check will be done between: 00:00 and 06:00 - 06:00 and 12:00 - 12:00 and 18:00 - 18:00 and 00:00.
            If you want to check the window between: 03:00 and 09:00 - 09:00 and 15:00 - 15:00 and 21:00 - 21:00 and 3:00, you will have to shift the window of 3 hours by settings windowAdvance: PT3H.""")
    @PluginProperty
    private Duration windowAdvance;

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
