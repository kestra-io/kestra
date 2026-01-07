package io.kestra.core.models.triggers;

import io.kestra.core.models.Label;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionTrigger;
import io.kestra.core.models.tasks.Output;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.RunContext;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.ListUtils;
import java.util.*;

public abstract class TriggerService {
    public static Execution generateExecution(
        AbstractTrigger trigger,
        ConditionContext conditionContext,
        TriggerContext context,
        Map<String, Object> variables
    ) {
        RunContext runContext = conditionContext.getRunContext();
        ExecutionTrigger executionTrigger = ExecutionTrigger.of(trigger, variables, runContext.logFileURI());

        return generateExecution(runContext.getTriggerExecutionId(), trigger, context, executionTrigger, conditionContext);
    }

    public static Execution generateExecution(
        AbstractTrigger trigger,
        ConditionContext conditionContext,
        TriggerContext context,
        Output output
    ) {
        RunContext runContext = conditionContext.getRunContext();
        ExecutionTrigger executionTrigger = ExecutionTrigger.of(trigger, output, runContext.logFileURI());

        return generateExecution(runContext.getTriggerExecutionId(), trigger, context, executionTrigger, conditionContext);
    }

    public static Execution generateRealtimeExecution(
        AbstractTrigger trigger,
        ConditionContext conditionContext,
        TriggerContext context,
        Output output
    ) {
        RunContext runContext = conditionContext.getRunContext();
        ExecutionTrigger executionTrigger = ExecutionTrigger.of(trigger, output, runContext.logFileURI());

        return generateExecution(IdUtils.create(), trigger, context, executionTrigger, conditionContext);
    }

    private static Execution generateExecution(
        String id,
        AbstractTrigger trigger,
        TriggerContext context,
        ExecutionTrigger executionTrigger,
        ConditionContext conditionContext
    ) {
        List<Label> executionLabels = new ArrayList<>(ListUtils.emptyOnNull(trigger.getLabels()));
        executionLabels.add(new Label(Label.FROM, "trigger"));
        if (executionLabels.stream().noneMatch(label -> Label.CORRELATION_ID.equals(label.key()))) {
            // add a correlation ID if none exist
            executionLabels.add(new Label(Label.CORRELATION_ID, id));
        }
        return Execution.builder()
            .id(id)
            .namespace(context.getNamespace())
            .flowId(context.getFlowId())
            .tenantId(context.getTenantId())
            .flowRevision(conditionContext.getFlow().getRevision())
            .variables(conditionContext.getFlow().getVariables())
            .state(new State())
            .trigger(executionTrigger)
            .labels(executionLabels)
            .build();
    }
}
