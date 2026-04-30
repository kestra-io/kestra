package io.kestra.core.services;

import java.util.Optional;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.triggers.multipleflows.Condition;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.multipleflows.MultipleCondition;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionWindow;
import io.kestra.core.runners.RunContext;

import io.kestra.core.utils.TruthUtils;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Singleton;

/**
 * Provides business logic to manipulate triggers <code>when</code> conditions,
 * and multiple flow conditions (Flow trigger <code>dependsOn</code>).
 */
@Singleton
public class ConditionService {
    /***
     * @return true if the condition is valid for the given flow and execution.
     */
    public boolean isValid(Condition condition, FlowInterface flow, Execution execution, RunContext runContext) {
        ConditionContext conditionContext = this.conditionContext(
            runContext,
            flow,
            execution
        );

        try {
            return condition.test(conditionContext);
        } catch (Exception e) {
            logException(flow, condition, conditionContext.getRunContext(), e);
            return false;
        }
    }

    /**
     * @return true if the trigger <code>when</code>condition is valid for the given flow and run context.
     */
    public boolean isValid(AbstractTrigger trigger, Flow flow, RunContext runContext) {
        return !isNotValid(flow, runContext, trigger.getWhen());
    }

    /**
     * @return true if the multiple condition is valid for the given flow and run context.
     */
    public boolean isValid(MultipleCondition dependsOn, Flow flow, Execution execution, Optional<MultipleConditionWindow> triggerExecutionWindow, RunContext runContext) {
        if (dependsOn == null || dependsOn.getConditions() == null) {
            // important to do it here avoid creating a costly conditionContext if not needed
            return true;
        }

        ConditionContext conditionContext = this.conditionContext(
            runContext,
            flow,
            execution
        );

        try {
            return dependsOn.test(conditionContext, triggerExecutionWindow);
        } catch (Exception e) {
            logException(flow, dependsOn, conditionContext.getRunContext(), e);

            return false;
        }
    }

    /**
     * Creates a condition context for the given flow, execution, and run context.
     */
    public ConditionContext conditionContext(RunContext runContext, FlowInterface flow, @Nullable Execution execution) {
        return ConditionContext.builder()
            .flow(flow)
            .execution(execution)
            .runContext(runContext)
            .build();
    }

    private boolean isNotValid(FlowInterface flow, RunContext runContext, String when) {
        try {
            return TruthUtils.isFalsy(runContext.render(when));
        } catch (IllegalVariableEvaluationException e) {
            logException(flow, when, runContext, e);

            return true;
        }
    }

    private void logException(FlowInterface flow, Object condition, RunContext runContext, Exception e) {
        runContext.logger().warn(
            "[namespace: {}] [flow: {}] [condition: {}] Evaluate Condition Failed with error '{}'",
            flow.getNamespace(),
            flow.getId(),
            condition.toString(),
            e.getMessage(),
            e
        );
    }
}
