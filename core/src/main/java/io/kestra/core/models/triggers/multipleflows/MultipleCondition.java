package io.kestra.core.models.triggers.multipleflows;

import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.triggers.TimeWindow;
// FIXME check if we keep it or not, maybe refactor the whole multiple flow handling and simplify it.
//  At least, if we keep it, we should make it sealed so it's not implemented wildly.
public interface MultipleCondition {
    String getId();

    TimeWindow getTimeWindow();

    Boolean getResetOnSuccess();

    Map<String, Condition> getConditions();

    Logger logger();

    Mode getMode();

    Integer getMinSatisfied();

    /**
     * This set of conditions will only validate previously calculated value on
     * io.kestra.executor.FlowTriggerService#computeExecutionsFromFlowTriggers(Execution, List, Optional) by the executor.
     * The real validation is done here.
     */
    default boolean test(ConditionContext conditionContext, Optional<MultipleConditionWindow> triggerExecutionWindow) throws InternalException {
        Map<String, Boolean> results = getConditions()
            .keySet()
            .stream()
            .map(
                condition -> new AbstractMap.SimpleEntry<>(
                    condition,
                    (triggerExecutionWindow.isPresent() &&
                        triggerExecutionWindow.get().getResults() != null &&
                        triggerExecutionWindow.get().getResults().containsKey(condition) &&
                        triggerExecutionWindow.get().getResults().get(condition))
                )
            )
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        long validatedCount = results
            .entrySet()
            .stream()
            .filter(Map.Entry::getValue)
            .count();

        boolean result = switch(getMode()) {
            case Mode.ALL -> getConditions().size() == validatedCount;
            case Mode.ANY -> validatedCount > 0;
            case Mode.AT_LEAST -> validatedCount >= getMinSatisfied();
        };

        Logger log = logger();
        if (result && log.isDebugEnabled()) {
            log.debug(
                "[namespace: {}] [flow: {}] Multiple conditions validated!",
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

    /**
     * Determines whether a multiple condition is satisfied based on its mode and the current window results.
     * Used to decide whether to purge the condition window after a successful evaluation.
     */
    default boolean isConditionSatisfied(MultipleConditionWindow window) {
        // MultipleConditionWindow.with() only stores true entries, so size() == number of satisfied conditions
        int satisfiedCount = Optional.ofNullable(window.getResults()).map(Map::size).orElse(0);
        return switch (getMode()) {
            case ALL -> getConditions().size() == satisfiedCount;
            case ANY -> satisfiedCount > 0;
            case AT_LEAST -> satisfiedCount >= getMinSatisfied();
        };
    }

    enum Mode {
        ALL, ANY, AT_LEAST
    }
}
