package io.kestra.core.services;

import java.util.*;
import java.util.stream.Collectors;

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
     * from the flow processed for runtime, which only {@link #forExecution} is given. Folding the raw flow's
     * labels in here would make them win that merge, where the contributed ones are appended last.
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
     * This is the single definition of that precedence. A route contributing labels to a {@code Create} command
     * calls {@link #withCorrelationId} alone instead: the executor applies this merge when it builds the
     * execution from the flow processed for runtime, so contributing the flow's own would double-count them and
     * let an authored value overrule governance.
     */
    public static List<Label> forExecution(FlowInterface flow, @Nullable List<Label> contributed, String executionId) {
        List<Label> labels = new ArrayList<>(labelsExcludingSystem(flow.getLabels()));
        labels.addAll(ListUtils.emptyOnNull(contributed));

        return withCorrelationId(Label.deduplicate(labels), executionId);
    }

    /**
     * Returns the contributed labels minus the keys governance force-set on the flow: the policy already
     * overruled the author, so it must overrule whoever starts the execution too, and the execution takes
     * those keys from the flow instead.
     */
    public static List<Label> withoutPinned(@Nullable List<Label> labels, Set<String> pinnedLabelKeys) {
        List<Label> contributed = ListUtils.emptyOnNull(labels);
        if (pinnedLabelKeys.isEmpty()) {
            return contributed;
        }

        return contributed.stream().filter(label -> !pinnedLabelKeys.contains(label.key())).toList();
    }

    /**
     * Returns the pinned keys the given labels carry a different value for than the flow does, i.e. the ones
     * whoever starts the execution actually tried to overrule.
     * <p>
     * A key echoing the flow's own value overrode nothing and is deliberately not reported: a route that builds
     * its execution before emitting its {@code Create} contributes the flow's labels along with its own, so
     * reporting every pinned key {@link #withoutPinned} drops would accuse those routes of an override the
     * caller never attempted.
     */
    public static Set<String> overriddenPinnedKeys(FlowInterface flow, @Nullable List<Label> labels, Set<String> pinnedLabelKeys) {
        if (pinnedLabelKeys.isEmpty()) {
            return Set.of();
        }

        Map<String, String> governed = Label.toMap(flow.getLabels());

        return ListUtils.emptyOnNull(labels).stream()
            .filter(label -> pinnedLabelKeys.contains(label.key()))
            .filter(label -> !Objects.equals(label.value(), governed.get(label.key())))
            .map(Label::key)
            .collect(Collectors.toCollection(LinkedHashSet::new));
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
