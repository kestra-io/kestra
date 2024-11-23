package io.kestra.core.models.triggers.multipleflows;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.conditions.Condition;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.triggers.TimeWindow;
import io.kestra.core.utils.Rethrow;
import org.slf4j.Logger;

import java.util.*;
import java.util.stream.Collectors;

public interface MultipleCondition extends Rethrow.PredicateChecked<ConditionContext, InternalException> {
    String getId();

    TimeWindow getTimeWindow();

    Boolean getResetOnSuccess();

    Map<String, Condition> getConditions();

    Logger logger();

    /**
     * This conditions will only validate previously calculated value on
     * {@link io.kestra.core.services.FlowTriggerService#computeExecutionsFromFlowTriggers(Execution, List, Optional)}} and {@link MultipleConditionStorageInterface#save(List)} by the executor.
     * The real validation is done here.
     */
    @Override
    default boolean test(ConditionContext conditionContext) throws InternalException {
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
}
