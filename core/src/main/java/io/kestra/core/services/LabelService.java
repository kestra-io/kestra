package io.kestra.core.services;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.Label;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.runners.RunContext;
import io.kestra.core.utils.ListUtils;
import jakarta.annotation.Nullable;

import java.util.*;


public final class LabelService {
    private LabelService() {}

    /**
     * Return labels after excluding system labels.
     * This method is used generally for any labels list
     * When labels list is null it handles it implicitly to prevent unnecessary null checks at the callers
     */
    public static List<Label> labelsExcludingSystem(List<Label> labels) {
        return ListUtils.emptyOnNull(labels)
                .stream()
                .filter(label -> !label.key().startsWith(Label.SYSTEM_PREFIX))
                .toList();
    }

    /**
     * Return flow labels excluding system labels concatenated with trigger labels.
     *
     * Trigger labels will be rendered via the run context but not flow labels.
     * In case rendering is not possible, the label will be omitted.
     */
    public static List<Label> fromTrigger(RunContext runContext, FlowInterface flow, AbstractTrigger trigger) {

        final List<Label> labels = new ArrayList<>(labelsExcludingSystem(flow.getLabels())); // no need for rendering

        // It is better to remove system labels before rendering
            List<Label> triggerLabels = labelsExcludingSystem(trigger.getLabels());
            for (Label label : triggerLabels) {
                final var value = renderLabelValue(runContext, label);
                if (value != null) {
                    labels.add(new Label(label.key(), value));
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
}
