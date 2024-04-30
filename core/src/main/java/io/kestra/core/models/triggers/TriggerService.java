package io.kestra.core.models.triggers;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.Label;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionTrigger;
import io.kestra.core.models.tasks.Output;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.RunContext;
import io.kestra.core.utils.IdUtils;

import java.util.Map;

public abstract class TriggerService {
    public static Execution generateExecution(
        AbstractTrigger trigger,
        ConditionContext conditionContext,
        TriggerContext context,
        Map<String, Object> variables
    ) {
        ExecutionTrigger executionTrigger = ExecutionTrigger.of(trigger, variables);
        RunContext runContext = conditionContext.getRunContext();

        return generateExecution(runContext.getTriggerExecutionId(), trigger, context, executionTrigger);
    }

    public static Execution generateExecution(
        AbstractTrigger trigger,
        ConditionContext conditionContext,
        TriggerContext context,
        Output output
    ) {
        ExecutionTrigger executionTrigger = ExecutionTrigger.of(trigger, output);
        RunContext runContext = conditionContext.getRunContext();

        return generateExecution(runContext.getTriggerExecutionId(), trigger, context, executionTrigger);
    }

    public static Execution generateRealtimeExecution(
        AbstractTrigger trigger,
        TriggerContext context,
        Output output
    ) {
        ExecutionTrigger executionTrigger = ExecutionTrigger.of(trigger, output);

        return generateExecution(IdUtils.create(), trigger, context, executionTrigger);
    }

    private static Execution generateExecution(
        String id,
        AbstractTrigger trigger,
        TriggerContext context,
        ExecutionTrigger executionTrigger
    ) {
        return Execution.builder()
            .id(id)
            .namespace(context.getNamespace())
            .flowId(context.getFlowId())
            .flowRevision(context.getFlowRevision())
            .state(new State())
            .trigger(executionTrigger)
            .labels(trigger.getLabels() == null ? null : trigger.getLabels())
            .build();
    }
}
