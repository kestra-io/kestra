package io.kestra.core.services;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.Label;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.runners.RunContext;
import io.kestra.core.utils.ListUtils;

import java.util.ArrayList;
import java.util.List;

public final class LabelService {
    private LabelService() {}

    /**
     * Return flow labels excluding system labels.
     */
    public static List<Label> labelsExcludingSystem(Flow flow) {
        return ListUtils.emptyOnNull(flow.getLabels()).stream().filter(label -> !label.key().startsWith(Label.SYSTEM_PREFIX)).toList();
    }

    /**
     * Return flow labels excluding system labels concatenated with trigger labels.
     *
     * Trigger labels will be rendered via the run context but not flow labels.
     * In case rendering is not possible, the label will be omitted.
     */
    public static List<Label> fromTrigger(RunContext runContext, Flow flow, AbstractTrigger trigger) {
        final List<Label> labels = new ArrayList<>();

        if (flow.getLabels() != null) {
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
}
