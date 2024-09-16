package io.kestra.core.models.triggers;

import io.kestra.core.models.Label;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionTrigger;
import io.kestra.core.models.tasks.Output;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.DefaultRunContext;
import io.kestra.core.runners.FlowInputOutput;
import io.kestra.core.runners.RunContext;
import io.kestra.core.utils.IdUtils;

import java.time.ZonedDateTime;
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

    public static Execution generateScheduledExecution(
        AbstractTrigger trigger,
        ConditionContext conditionContext,
        TriggerContext context,
        List<Label> labels,
        Map<String, Object> inputs,
        Map<String, Object> variables,
        Optional<ZonedDateTime> scheduleDate
    ) {
        RunContext runContext = conditionContext.getRunContext();
        ExecutionTrigger executionTrigger = ExecutionTrigger.of(trigger, variables);

        Execution execution = Execution.builder()
            .id(runContext.getTriggerExecutionId())
            .tenantId(context.getTenantId())
            .namespace(context.getNamespace())
            .flowId(context.getFlowId())
            .flowRevision(conditionContext.getFlow().getRevision())
            .labels(labels)
            .state(new State())
            .trigger(executionTrigger)
            .scheduleDate(scheduleDate.map(date -> date.toInstant()).orElse(null))
            .build();

        Map<String, Object> allInputs = new HashMap<>();
        // add flow inputs with default value
        var flow = conditionContext.getFlow();
        if (flow.getInputs() != null) {
            flow.getInputs().stream()
                .filter(input -> input.getDefaults() != null)
                .forEach(input -> allInputs.put(input.getId(), input.getDefaults()));
        }

        if (inputs != null) {
            allInputs.putAll(inputs);
        }

        // add inputs and inject defaults
        if (!allInputs.isEmpty()) {
            FlowInputOutput flowInputOutput = ((DefaultRunContext)runContext).getApplicationContext().getBean(FlowInputOutput.class);
            execution = execution.withInputs(flowInputOutput.typedInputs(conditionContext.getFlow(), execution, allInputs));
        }

        return execution;
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
