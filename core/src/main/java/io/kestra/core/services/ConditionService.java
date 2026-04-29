package io.kestra.core.services;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.conditions.Condition;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.multipleflows.MultipleCondition;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionWindow;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.ListUtils;

import io.kestra.core.utils.TruthUtils;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import static io.kestra.core.utils.Rethrow.throwPredicate;

/**
 * Provides business logic to manipulate {@link Condition}
 */
@Singleton
public class ConditionService {
    @Inject
    private RunContextFactory runContextFactory;

    public boolean isValid(Condition condition, FlowInterface flow, Execution execution) {
        ConditionContext conditionContext = this.conditionContext(
            runContextFactory.of(flow, execution),
            flow,
            execution
        );

        return this.valid(flow, Collections.singletonList(condition), conditionContext);
    }

    /**
     * Check that all conditions are valid.
     * Warning, this method throws if a condition cannot be evaluated.
     */
    public boolean areValid(List<Condition> conditions, ConditionContext conditionContext) throws InternalException {
        return conditions
            .stream()
            .allMatch(throwPredicate(condition -> condition.test(conditionContext)));
    }

    public boolean isValid(AbstractTrigger trigger, Flow flow, Execution execution) {
        RunContext runContext = runContextFactory.of(flow, execution);
        return this.isValid(trigger, flow, execution, runContext);
    }

    public boolean isValid(AbstractTrigger trigger, Flow flow, Execution execution, RunContext runContext) {
        if (isNotValid(flow, runContext, trigger.getWhen())) {
            return false;
        }

        if (ListUtils.isEmpty(trigger.getConditions())) {
            // important to do it here avoid creating a costly conditionContext if not needed
            return true;
        }

        ConditionContext conditionContext = this.conditionContext(
            runContext,
            flow,
            execution
        );

        return this.valid(flow, trigger.getConditions(), conditionContext);
    }

    public boolean isValid(MultipleCondition dependsOn, Flow flow, Execution execution, Optional<MultipleConditionWindow> triggerExecutionWindow) {
        if (dependsOn == null || dependsOn.getConditions() == null) {
            // important to do it here avoid creating a costly conditionContext if not needed
            return true;
        }

        ConditionContext conditionContext = this.conditionContext(
            runContextFactory.of(flow, execution),
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

    public ConditionContext conditionContext(RunContext runContext, FlowInterface flow, @Nullable Execution execution) {
        return ConditionContext.builder()
            .flow(flow)
            .execution(execution)
            .runContext(runContext)
            .build();
    }

    private boolean valid(FlowInterface flow, List<Condition> list, ConditionContext conditionContext) {
        return list
            .stream()
            .allMatch(condition ->
            {
                try {
                    return condition.test(conditionContext);
                } catch (Exception e) {
                    logException(flow, condition, conditionContext.getRunContext(), e);

                    return false;
                }
            });
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
