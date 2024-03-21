package io.kestra.core.models.triggers;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.Label;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionTrigger;
import io.kestra.core.models.tasks.Output;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.RunContext;

import java.util.Map;

public abstract class TriggerService {
    public static Execution generateExecution(
        AbstractTrigger trigger,
        ConditionContext conditionContext,
        TriggerContext context,
        Map<String, Object> variables
    ) {
        ExecutionTrigger executionTrigger = ExecutionTrigger.of(trigger, variables);

        return generateExecution(trigger, conditionContext, context, executionTrigger);
    }

    public static Execution generateExecution(
        AbstractTrigger trigger,
        ConditionContext conditionContext,
        TriggerContext context,
        Output output
    ) {
        ExecutionTrigger executionTrigger = ExecutionTrigger.of(trigger, output);

        return generateExecution(trigger, conditionContext, context, executionTrigger);
    }

    private static Execution generateExecution(
        AbstractTrigger trigger,
        ConditionContext conditionContext,
        TriggerContext context,
        ExecutionTrigger executionTrigger
    ) {
        RunContext runContext = conditionContext.getRunContext();

        return Execution.builder()
            .id(runContext.getTriggerExecutionId())
            .namespace(context.getNamespace())
            .flowId(context.getFlowId())
            .flowRevision(context.getFlowRevision())
            .state(new State())
            .trigger(executionTrigger)
            .labels(trigger.getLabels() == null ? null : trigger.getLabels())
            .build();
    }
}
