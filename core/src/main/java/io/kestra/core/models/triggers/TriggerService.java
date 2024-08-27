package io.kestra.core.models.triggers;

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
        RunContext runContext = conditionContext.getRunContext();
        ExecutionTrigger executionTrigger = ExecutionTrigger.of(trigger, variables, runContext.logFileURI());

        return generateExecution(runContext.getTriggerExecutionId(), trigger, context, executionTrigger, conditionContext.getFlow().getRevision());
    }

    public static Execution generateExecution(
        AbstractTrigger trigger,
        ConditionContext conditionContext,
        TriggerContext context,
        Output output
    ) {
        RunContext runContext = conditionContext.getRunContext();
        ExecutionTrigger executionTrigger = ExecutionTrigger.of(trigger, output, runContext.logFileURI());

        return generateExecution(runContext.getTriggerExecutionId(), trigger, context, executionTrigger, conditionContext.getFlow().getRevision());
    }

    public static Execution generateRealtimeExecution(
        AbstractTrigger trigger,
        ConditionContext conditionContext,
        TriggerContext context,
        Output output
    ) {
        RunContext runContext = conditionContext.getRunContext();
        ExecutionTrigger executionTrigger = ExecutionTrigger.of(trigger, output, runContext.logFileURI());

        return generateExecution(IdUtils.create(), trigger, context, executionTrigger, conditionContext.getFlow().getRevision());
    }

    private static Execution generateExecution(
        String id,
        AbstractTrigger trigger,
        TriggerContext context,
        ExecutionTrigger executionTrigger,
        Integer flowRevision
    ) {
        return Execution.builder()
            .id(id)
            .namespace(context.getNamespace())
            .flowId(context.getFlowId())
            .flowRevision(flowRevision)
            .state(new State())
            .trigger(executionTrigger)
            .labels(trigger.getLabels() == null ? null : trigger.getLabels())
            .build();
    }
}
