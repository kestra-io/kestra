package io.kestra.core.models.triggers;

import java.util.*;

import io.kestra.core.models.Label;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionTrigger;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.Output;
import io.kestra.core.runners.RunContext;
import io.kestra.core.services.LabelService;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.ListUtils;

public abstract class TriggerService {

    /**
     * Generate a {@link TriggerEvaluationResult} from raw trigger output variables.
     */
    public static TriggerEvaluationResult generateEvaluationResult(
        AbstractTrigger trigger,
        ConditionContext conditionContext,
        Map<String, Object> variables) {
        RunContext runContext = conditionContext.getRunContext();
        ExecutionTrigger executionTrigger = ExecutionTrigger.of(trigger, variables, runContext.logFileURI());

        return buildEvaluationResult(runContext.getTriggerExecutionId(), trigger, executionTrigger, conditionContext);
    }

    /**
     * Generate a {@link TriggerEvaluationResult} from a typed trigger output.
     */
    public static TriggerEvaluationResult generateEvaluationResult(
        AbstractTrigger trigger,
        ConditionContext conditionContext,
        Output output) {
        RunContext runContext = conditionContext.getRunContext();
        ExecutionTrigger executionTrigger = ExecutionTrigger.of(trigger, output, runContext.logFileURI());

        return buildEvaluationResult(runContext.getTriggerExecutionId(), trigger, executionTrigger, conditionContext);
    }

    /**
     * Generate a {@link TriggerEvaluationResult} for a realtime trigger.
     */
    public static TriggerEvaluationResult generateRealtimeEvaluationResult(
        AbstractTrigger trigger,
        ConditionContext conditionContext,
        Output output) {
        RunContext runContext = conditionContext.getRunContext();
        ExecutionTrigger executionTrigger = ExecutionTrigger.of(trigger, output, runContext.logFileURI());

        return buildEvaluationResult(IdUtils.create(), trigger, executionTrigger, conditionContext);
    }

    private static TriggerEvaluationResult buildEvaluationResult(
        String id,
        AbstractTrigger trigger,
        ExecutionTrigger executionTrigger,
        ConditionContext conditionContext) {
        List<Label> labels = buildLabels(id, trigger, conditionContext);

        return new TriggerEvaluationResult(
            id,
            State.Type.CREATED,
            executionTrigger,
            labels,
            conditionContext.getFlow().getRevision()
        );
    }

    /**
     * @deprecated Use {@link #generateEvaluationResult(AbstractTrigger, ConditionContext, Map)} instead.
     */
    @Deprecated(forRemoval = true, since = "2.0.0")
    public static Execution generateExecution(
        AbstractTrigger trigger,
        ConditionContext conditionContext,
        TriggerContext context,
        Map<String, Object> variables) {
        RunContext runContext = conditionContext.getRunContext();
        ExecutionTrigger executionTrigger = ExecutionTrigger.of(trigger, variables, runContext.logFileURI());

        return generateExecution(runContext.getTriggerExecutionId(), trigger, context, executionTrigger, conditionContext);
    }

    /**
     * @deprecated Use {@link #generateEvaluationResult(AbstractTrigger, ConditionContext, Output)} instead.
     */
    @Deprecated(forRemoval = true, since = "2.0.0")
    public static Execution generateExecution(
        AbstractTrigger trigger,
        ConditionContext conditionContext,
        TriggerContext context,
        Output output) {
        RunContext runContext = conditionContext.getRunContext();
        ExecutionTrigger executionTrigger = ExecutionTrigger.of(trigger, output, runContext.logFileURI());

        return generateExecution(runContext.getTriggerExecutionId(), trigger, context, executionTrigger, conditionContext);
    }

    /**
     * @deprecated Use {@link #generateRealtimeEvaluationResult(AbstractTrigger, ConditionContext, Output)} instead.
     */
    @Deprecated(forRemoval = true, since = "2.0.0")
    public static Execution generateRealtimeExecution(
        AbstractTrigger trigger,
        ConditionContext conditionContext,
        TriggerContext context,
        Output output) {
        RunContext runContext = conditionContext.getRunContext();
        ExecutionTrigger executionTrigger = ExecutionTrigger.of(trigger, output, runContext.logFileURI());

        return generateExecution(IdUtils.create(), trigger, context, executionTrigger, conditionContext);
    }

    private static Execution generateExecution(
        String id,
        AbstractTrigger trigger,
        TriggerContext context,
        ExecutionTrigger executionTrigger,
        ConditionContext conditionContext) {
        List<Label> executionLabels = buildLabels(id, trigger, conditionContext);
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

    private static List<Label> buildLabels(String id, AbstractTrigger trigger, ConditionContext conditionContext) {
        List<Label> labels = new ArrayList<>(ListUtils.emptyOnNull(trigger.getLabels()));
        labels.add(new Label(Label.FROM, "trigger"));
        if (labels.stream().noneMatch(label -> Label.CORRELATION_ID.equals(label.key()))) {
            labels.add(new Label(Label.CORRELATION_ID, id));
        }
        // include non-system flow labels (previously added in WorkerTriggerProcessor)
        labels.addAll(LabelService.labelsExcludingSystem(conditionContext.getFlow().getLabels()));
        return labels;
    }
}
