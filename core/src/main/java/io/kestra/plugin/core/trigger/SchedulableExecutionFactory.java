package io.kestra.plugin.core.trigger;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.Label;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionTrigger;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.Backfill;
import io.kestra.core.models.triggers.Schedulable;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.runners.RunContext;
import io.kestra.core.services.LabelService;
import io.kestra.core.utils.ListUtils;

import java.time.ZonedDateTime;
import java.time.chrono.ChronoZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Factory class for constructing a new {@link Execution} from a {@link Schedulable} trigger.
 *
 * @see io.kestra.plugin.core.trigger.Schedule
 * @see io.kestra.plugin.core.trigger.ScheduleOnDates
 */
final class SchedulableExecutionFactory {

    static Execution createFailedExecution(Schedulable trigger, ConditionContext conditionContext, TriggerContext triggerContext) throws IllegalVariableEvaluationException {
        return Execution.builder()
            .id(conditionContext.getRunContext().getTriggerExecutionId())
            .tenantId(triggerContext.getTenantId())
            .namespace(triggerContext.getNamespace())
            .flowId(triggerContext.getFlowId())
            .flowRevision(conditionContext.getFlow().getRevision())
            .labels(SchedulableExecutionFactory.getLabels(trigger, conditionContext.getRunContext(), triggerContext.getBackfill(), conditionContext.getFlow()))
            .state(new State().withState(State.Type.FAILED))
            .build();
    }

    static Execution createExecution(Schedulable trigger, ConditionContext conditionContext, TriggerContext triggerContext, Map<String, Object> variables, ZonedDateTime scheduleDate) throws IllegalVariableEvaluationException {
        RunContext runContext = conditionContext.getRunContext();
        ExecutionTrigger executionTrigger = ExecutionTrigger.of((AbstractTrigger) trigger, variables);

        List<Label> labels = getLabels(trigger, runContext, triggerContext.getBackfill(), conditionContext.getFlow());

        List<Label> executionLabels = new ArrayList<>(ListUtils.emptyOnNull(labels));
        executionLabels.add(new Label(Label.FROM, "trigger"));
        if (executionLabels.stream().noneMatch(label -> Label.CORRELATION_ID.equals(label.key()))) {
            // add a correlation ID if none exist
            executionLabels.add(new Label(Label.CORRELATION_ID, runContext.getTriggerExecutionId()));
        }

        Execution execution = Execution.builder()
            .id(runContext.getTriggerExecutionId())
            .tenantId(triggerContext.getTenantId())
            .namespace(triggerContext.getNamespace())
            .flowId(triggerContext.getFlowId())
            .flowRevision(conditionContext.getFlow().getRevision())
            .variables(conditionContext.getFlow().getVariables())
            .labels(executionLabels)
            .state(new State())
            .trigger(executionTrigger)
            .scheduleDate(Optional.ofNullable(scheduleDate).map(ChronoZonedDateTime::toInstant).orElse(null))
            .build();

        Map<String, Object> allInputs = getInputs(trigger, runContext, triggerContext.getBackfill());

        // add inputs and inject defaults (FlowInputOutput handles defaults internally)
        execution = execution.withInputs(runContext.inputAndOutput().readInputs(conditionContext.getFlow(), execution, allInputs));

        return execution;
    }

    private static Map<String, Object> getInputs(Schedulable trigger, RunContext runContext, Backfill backfill) throws IllegalVariableEvaluationException {
        Map<String, Object> inputs = new HashMap<>();

        if (trigger.getInputs() != null) {
            inputs.putAll(runContext.render(trigger.getInputs()));
        }

        if (backfill != null && backfill.getInputs() != null) {
            inputs.putAll(runContext.render(backfill.getInputs()));
        }

        return inputs;
    }

    private static List<Label> getLabels(Schedulable trigger, RunContext runContext, Backfill backfill, FlowInterface flow) throws IllegalVariableEvaluationException {
        List<Label> labels = LabelService.fromTrigger(runContext, flow, (AbstractTrigger) trigger);

        if (backfill != null && backfill.getLabels() != null) {
            for (Label label : backfill.getLabels()) {
                final var value = runContext.render(label.value());
                if (value != null) {
                    labels.add(new Label(label.key(), value));
                }
            }
        }
        return labels;
    }
}
