package io.kestra.core.services;

import java.util.*;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.Label;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.runners.RunContext;
import io.kestra.core.utils.ListUtils;

import jakarta.annotation.Nullable;

public final class LabelService {
    private LabelService() {
    }

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
     * Return the trigger's own labels, rendered via the run context, excluding system labels.
     * In case rendering is not possible, the label will be omitted.
     * <p>
     * Deliberately excludes the flow's labels: an execution snapshots those at creation time and must take them
     * from the flow processed for runtime, which only {@link io.kestra.core.models.executions.Execution#newExecution}
     * is given. Folding the raw flow's labels in here would make them win the creation-time merge — see
     * {@link io.kestra.core.services.ExecutionService#create}, where these labels are appended last.
     */
    public static List<Label> fromTrigger(RunContext runContext, AbstractTrigger trigger, Map<String, Object> variables) {
        final List<Label> labels = new ArrayList<>();

        // It is better to remove system labels before rendering
        List<Label> triggerLabels = labelsExcludingSystem(trigger.getLabels());
        for (Label label : triggerLabels) {
            final var value = renderLabelValue(runContext, label, variables);
            if (value != null) {
                labels.add(new Label(label.key(), value));
            }
        }

        return labels;
    }

    /**
     * Merges the labels an execution carries at creation time: the flow's own — system labels stripped, as a
     * flow must not author them — overridden by those the trigger or caller contributes, plus a correlation id
     * when none is present.
     * <p>
     * This is the single definition of label precedence at creation time, and the only place the flow's labels
     * are read: every creation route goes through it so a caller cannot overrule governance on one route and
     * not another.
     *
     * @param flow the flow the execution will run, which must be the flow processed for runtime
     * @param contributed the labels the trigger or caller contributes, which override the flow's
     * @param executionId the execution's id, used as correlation id when none is contributed
     */
    public static List<Label> forExecution(FlowInterface flow, @Nullable List<Label> contributed, String executionId) {
        List<Label> labels = new ArrayList<>(labelsExcludingSystem(flow.getLabels()));
        labels.addAll(ListUtils.emptyOnNull(contributed));

        return withCorrelationId(Label.deduplicate(labels), executionId);
    }

    /**
     * Returns the given labels with a correlation id added when none is present. An existing one is never
     * replaced: a child execution inherits its parent's, which is what correlates the two.
     */
    public static List<Label> withCorrelationId(@Nullable List<Label> labels, String executionId) {
        List<Label> withCorrelationId = new ArrayList<>(ListUtils.emptyOnNull(labels));
        if (withCorrelationId.stream().noneMatch(label -> Label.CORRELATION_ID.equals(label.key()))) {
            withCorrelationId.add(new Label(Label.CORRELATION_ID, executionId));
        }

        return withCorrelationId;
    }

    private static String renderLabelValue(RunContext runContext, Label label, Map<String, Object> variables) {
        try {
            String value = runContext.render(label.value(), variables);
            return (value != null && !value.isEmpty()) ? value : null;
        } catch (IllegalVariableEvaluationException e) {
            runContext.logger().warn("Failed to render label '{}', it will be omitted", label.key(), e);
            return null;
        }
    }

    public static boolean containsAll(@Nullable List<Label> labelsContainer, @Nullable List<Label> labelsThatMustBeIncluded) {
        Map<String, String> labelsContainerMap = ListUtils.emptyOnNull(labelsContainer).stream().collect(HashMap::new, (m, label) -> m.put(label.key(), label.value()), HashMap::putAll);

        return ListUtils.emptyOnNull(labelsThatMustBeIncluded).stream().allMatch(label -> Objects.equals(labelsContainerMap.get(label.key()), label.value()));
    }
}
