package io.kestra.core.services;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.Label;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.Backfill;
import io.kestra.core.models.triggers.Schedulable;
import io.kestra.core.runners.RunContext;
import io.kestra.core.utils.ListUtils;
import jakarta.annotation.Nullable;

import java.util.*;


public final class LabelService {
    private LabelService() {}

    /**
     * Return flow labels excluding system labels.
     */
    public static List<Label> labelsExcludingSystem(FlowInterface flow) {
        return ListUtils.emptyOnNull(flow.getLabels()).stream().filter(label -> !label.key().startsWith(Label.SYSTEM_PREFIX)).toList();
    }

    /**
     * Return labels excluding system labels.
     */
    public static List<Label> labelsExcludingSystem(List<Label> labels) {
        return ListUtils.emptyOnNull(labels).stream().filter(label -> !label.key().startsWith(Label.SYSTEM_PREFIX)).toList();
    }

    /**
     * Return flow labels excluding system labels concatenated with trigger labels.
     *
     * Trigger labels will be rendered via the run context but not flow labels.
     * In case rendering is not possible, the label will be omitted.
     */
    public static List<Label> fromTrigger(RunContext runContext,@Nullable FlowInterface flow, AbstractTrigger trigger) {
        final List<Label> labels = new ArrayList<>();

        if (flow != null && flow.getLabels() != null) {
            labels.addAll(LabelService.labelsExcludingSystem(flow)); // no need for rendering
        }

        if (trigger.getLabels() != null) {
            for (Label label : trigger.getLabels()) {
                final var value = renderLabelValue(runContext, label);
                if (value != null) {
                    labels.add(new Label(label.key(), value));
                }
            }
        }

        return labels;
    }

    private static String renderLabelValue(RunContext runContext, Label label) {
        try {
            return runContext.render(label.value());
        } catch (IllegalVariableEvaluationException e) {
            runContext.logger().warn("Failed to render label '{}', it will be omitted", label.key(), e);
            return null;
        }
    }

    public static boolean containsAll(@Nullable List<Label> labelsContainer, @Nullable List<Label> labelsThatMustBeIncluded) {
        Map<String, String> labelsContainerMap = ListUtils.emptyOnNull(labelsContainer).stream().collect(HashMap::new, (m, label)-> m.put(label.key(), label.value()), HashMap::putAll);

        return ListUtils.emptyOnNull(labelsThatMustBeIncluded).stream().allMatch(label -> Objects.equals(labelsContainerMap.get(label.key()), label.value()));
    }

    public static List<Label> getLabels(Schedulable trigger, RunContext runContext, Backfill backfill) throws IllegalVariableEvaluationException{
       return getLabels(trigger, runContext, backfill, null);
    }
    public static List<Label> getLabels(Schedulable trigger, RunContext runContext, Backfill backfill, @Nullable FlowInterface flow) {
        List<Label> labels = fromTrigger(runContext, flow, (AbstractTrigger) trigger);

        if (backfill != null && backfill.getLabels() != null) {
            for (Label label : backfill.getLabels()) {
                    final var value = renderLabelValue(runContext, label);
                    if (value != null) {
                        labels.add(new Label(label.key(), value));
                    }
            }
        }
        return labels;
    }
}
